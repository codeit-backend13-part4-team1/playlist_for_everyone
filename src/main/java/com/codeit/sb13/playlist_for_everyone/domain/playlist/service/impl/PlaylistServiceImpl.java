package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentDuplicatedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListForbiddenException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistServiceImpl implements PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final ContentRepository contentRepository;

  @Override
  @Transactional
  public PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request) {
    User user = userRepository.findById(ownerId)
        .orElseThrow(() -> new UserNotFoundException(
            Map.of("userId", ownerId)));

    Playlist playlist = new Playlist(user, request.title(), request.description());

    Playlist saved = playlistRepository.save(playlist);
    UserSummary userSummary = new UserSummary(user.getId(), user.getName(), null);

    return PlaylistResponse.from(saved, userSummary, false );
    // 방금 생성된 플레이리스트라 구독 정보가 없으므로 항상 false이다.

  }

  @Override
  public PlaylistResponse getPlaylist(UUID playlistId, UUID requesterId) {

    Playlist playlist = playlistRepository.findWithOwnerById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    User owner = playlist.getOwner();

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    UserSummary userSummary = new UserSummary(owner.getId(), owner.getName(), null);

    return PlaylistResponse.from(playlist, userSummary, subscribedByMe);
  }

  @Override
  public CursorResponse<PlaylistResponse> getPlaylists(
      PlaylistSearchRequest request, UUID requesterId) {

    PlaylistSearchCondition condition = toCondition(request);

    List<Playlist> fetched = playlistRepository.search(condition);
    long totalCount = playlistRepository.count(condition);

    boolean hasNext = fetched.size() > condition.limit();
    List<Playlist> page = hasNext ? fetched.subList(0, condition.limit()) : fetched;

    Set<UUID> subscribedIds = findSubscribedIds(page, requesterId);
    List<PlaylistResponse> data = page.stream()
        .map(playlist -> toResponse(playlist, subscribedIds.contains(playlist.getId())))
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      Playlist last = page.get(page.size() - 1);
      nextCursor = toCursor(last, condition.sortBy());
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount,
        condition.sortBy().getSortKey(), condition.sortDirection());
  }

  @Override
  @Transactional
  public PlaylistResponse updatePlaylist(
      UUID playlistId, PlaylistUpdateRequest request, UUID requesterId) {

    Playlist playlist = playlistRepository.findWithOwnerById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)
        ));

    if(!playlist.isOwnedBy(requesterId)) {
      throw new PlayListForbiddenException(
          Map.of("playlistId", playlistId, "requesterId", requesterId));
    }

    playlist.update(request.title(), request.description());

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    return toResponse(playlist,subscribedByMe);

  }

  @Override
  @Transactional
  public void deletePlaylist(UUID playlistId, UUID requesterId) {

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)
        ));

    if (!playlist.isOwnedBy(requesterId)) {
      throw new PlayListForbiddenException(
          Map.of("playlistId", playlistId, "requesterId", requesterId));
    }

    subscriptionRepository.deleteAllByPlaylistId(playlistId);
    playlistContentRepository.deleteAllByPlaylistId(playlistId);
    playlistRepository.delete(playlist);
  }

  @Override
  @Transactional
  public void addContent(UUID playlistId, UUID contentId, UUID requesterId) {

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)
        ));

    if(!playlist.isOwnedBy(requesterId)) {
      throw  new PlayListForbiddenException(
          Map.of("playlistId", playlistId, "requesterId", requesterId)
      );
    }

    if(!contentRepository.existsById(contentId)) {
      throw  new ContentNotFoundException(Map.of("contentId", contentId));
    }

    if (playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId)) {
      throw new PlayListContentDuplicatedException(
          Map.of("playlistId", playlistId, "contentId", contentId));
    }

    playlistContentRepository.save(new PlaylistContent(playlistId, contentId));
    playlistRepository.touchUpdatedAt(playlistId, LocalDateTime.now());
  }

  @Override
  @Transactional
  public void removeContent(UUID playlistId, UUID contentId, UUID requesterId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    if (!playlist.isOwnedBy(requesterId)) {
      throw new PlayListForbiddenException(
          Map.of("playlistId", playlistId, "requesterId", requesterId));
    }

    int deleted = playlistContentRepository.deleteByPlaylistIdAndContentId(playlistId, contentId);
    if (deleted == 0) {
      throw new PlayListContentNotFoundException(
          Map.of("playlistId", playlistId, "contentId", contentId));
    }

    playlistRepository.touchUpdatedAt(playlistId, LocalDateTime.now());
  }


 //---------[헬퍼 메서드]
  private PlaylistSearchCondition toCondition(PlaylistSearchRequest request) {
    return new PlaylistSearchCondition(
        request.keywordLike(), request.ownerIdEqual(), request.subscriberIdEqual(),
        request.cursor(), request.idAfter(),
        request.limit(), request.sortBy(), request.sortDirection());
  }

  private Set<UUID> findSubscribedIds(List<Playlist> playlists, UUID requesterId) {
    if (playlists.isEmpty()) {
      return Set.of();
    }
    List<UUID> playlistIds = playlists.stream().map(Playlist::getId).toList();
    return new HashSet<>(
        subscriptionRepository.findSubscribedPlaylistIds(requesterId, playlistIds));
  }

  private String toCursor(Playlist playlist, PlaylistSortBy sortBy) {
    return switch (sortBy) {
      case UPDATED_AT -> playlist.getUpdatedAt().toString();
      case SUBSCRIBE_COUNT -> String.valueOf(playlist.getSubscriberCount());
    };
  }

  private PlaylistResponse toResponse(Playlist playlist, boolean subscribedByMe) {
    User owner = playlist.getOwner();
    UserSummary userSummary = new UserSummary(owner.getId(), owner.getName(), null);
    return PlaylistResponse.from(playlist, userSummary, subscribedByMe);
  }
}
