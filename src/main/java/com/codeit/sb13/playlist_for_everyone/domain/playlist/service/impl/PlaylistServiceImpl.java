package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Subscription;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentDuplicatedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListSubscribeOwnException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListSubscriptionDuplicatedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListSubscriptionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
  private final ApplicationEventPublisher eventPublisher;
  private final UserSummaryMapper  userSummaryMapper;
  private final PlaylistReader playlistReader;
  private final PlaylistContentSummaryMapper playlistContentSummaryMapper;

  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  @Override
  @Transactional
  public PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request) {
    User user = userRepository.findById(ownerId)
        .orElseThrow(() -> new UserNotFoundException(
            Map.of("userId", ownerId)));

    Playlist playlist = new Playlist(user, request.title(), request.description());

    Playlist saved = playlistRepository.save(playlist);

    eventPublisher.publishEvent(new PlaylistCreatedEvent(
        saved.getId(),
        saved.getTitle(),
        user.getId(),
        user.getName()));

    UserSummary userSummary = userSummaryMapper.toSummary(user);

    return PlaylistResponse.from(saved, userSummary, false, List.of());
    // 방금 생성된 플레이리스트라 구독 정보가 없으므로 항상 false이다.

  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PlaylistResponse getPlaylist(UUID playlistId, UUID requesterId) {
    // 조회 트랜잭션은 Reader에서 끝나고, 프로필 이미지 URL 생성은 트랜잭션 밖에서 한다.
    PlaylistReader.PlaylistDetail detail = playlistReader.readOne(playlistId, requesterId);
    return toResponse(detail.playlist(), detail.subscribedByMe(), detail.contents());
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CursorResponse<PlaylistResponse> getPlaylists(
      PlaylistSearchRequest request, UUID requesterId) {

    PlaylistSearchCondition condition = toCondition(request);

    PlaylistReader.PlaylistPage result = playlistReader.readPage(condition, requesterId);
    List<Playlist> page = result.playlists();

    List<PlaylistResponse> data = page.stream()
        .map(playlist -> toResponse(
            playlist,
            result.subscribedIds().contains(playlist.getId()),
            result.contents().getOrDefault(playlist.getId(), List.of())))
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (result.hasNext()) {
      Playlist last = page.get(page.size() - 1);
      nextCursor = toCursor(last, condition.sortBy());
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, result.hasNext(), result.totalCount(),
        condition.sortBy().getSortKey(), condition.sortDirection());
  }

  @Override
  @PreAuthorize("isAuthenticated() and @playlistGuard.canModify(#playlistId, #requesterId)")
  @Transactional
  public PlaylistResponse updatePlaylist(
      UUID playlistId, PlaylistUpdateRequest request, UUID requesterId) {

    Playlist playlist = playlistRepository.findWithOwnerById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)
        ));


    playlist.update(request.title(), request.description());

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    return toResponse(playlist, subscribedByMe);

  }

  @Override
  @PreAuthorize("isAuthenticated() and @playlistGuard.canModify(#playlistId, #requesterId)")
  @Transactional
  public void deletePlaylist(UUID playlistId, UUID requesterId) {

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)
        ));

    subscriptionRepository.deleteAllByPlaylistId(playlistId);
    playlistContentRepository.deleteAllByPlaylistId(playlistId);
    playlistRepository.delete(playlist);
  }

  @Override
  @PreAuthorize("isAuthenticated() and @playlistGuard.canModify(#playlistId, #requesterId)")
  @Transactional
  public void addContent(UUID playlistId, UUID contentId, UUID requesterId) {

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)
        ));

    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ContentNotFoundException(Map.of("contentId", contentId)));

    if (playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId)) {
      throw new PlayListContentDuplicatedException(
          Map.of("playlistId", playlistId, "contentId", contentId));
    }

    playlistContentRepository.save(new PlaylistContent(playlistId, contentId));
    playlistRepository.touchUpdatedAt(playlistId, LocalDateTime.now());

    eventPublisher.publishEvent(new PlaylistContentAddedEvent(
        playlistId,
        playlist.getTitle(),
        contentId,
        content.getTitle(),
        requesterId));
  }

  @Override
  @PreAuthorize("isAuthenticated() and @playlistGuard.canModify(#playlistId, #requesterId)")
  @Transactional
  public void removeContent(UUID playlistId, UUID contentId, UUID requesterId) {
    playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    int deleted = playlistContentRepository.deleteByPlaylistIdAndContentId(playlistId, contentId);
    if (deleted == 0) {
      throw new PlayListContentNotFoundException(
          Map.of("playlistId", playlistId, "contentId", contentId));
    }

    playlistRepository.touchUpdatedAt(playlistId, LocalDateTime.now());
  }

  @Override
  @Transactional
  public void subscribe(UUID playlistId, UUID subscriberId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    if (playlist.isOwnedBy(subscriberId)) {
      throw new PlayListSubscribeOwnException(
          Map.of("playlistId", playlistId, "subscriberId", subscriberId));
    }

    if (subscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, subscriberId)) {
      throw new PlayListSubscriptionDuplicatedException(
          Map.of("playlistId", playlistId, "subscriberId", subscriberId));
    }

    try {
      subscriptionRepository.saveAndFlush(new Subscription(playlistId, subscriberId));
    } catch (DataIntegrityViolationException e) {
      if (!isUniqueViolation(e)) {
        throw e;
      }
      throw new PlayListSubscriptionDuplicatedException(
          Map.of("playlistId", playlistId, "subscriberId", subscriberId), e);
    }

    playlistRepository.increaseSubscriberCount(playlistId);

    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new UserNotFoundException(Map.of("userId", subscriberId)));
    eventPublisher.publishEvent(new PlaylistSubscribedEvent(
        playlistId,
        playlist.getTitle(),
        playlist.getOwner().getId(),
        subscriberId,
        subscriber.getName()));

  }

  @Override
  @Transactional
  public void unsubscribe(UUID playlistId, UUID subscriberId) {
    if (!playlistRepository.existsById(playlistId)) {
      throw new PlayListNotFoundException(Map.of("playlistId", playlistId));
    }

    int deleted = subscriptionRepository.deleteByPlaylistIdAndSubscriberId(
        playlistId, subscriberId);
    if (deleted == 0) {
      throw new PlayListSubscriptionNotFoundException(
          Map.of("playlistId", playlistId, "subscriberId", subscriberId));
    }

    playlistRepository.decreaseSubscriberCount(playlistId);
  }

  @Override
  public List<UUID> findSubscriberIds(UUID playlistId) {
    return subscriptionRepository.findSubscriberIds(playlistId);
  }


  //---------[헬퍼 메서드]
  private PlaylistSearchCondition toCondition(PlaylistSearchRequest request) {
    return new PlaylistSearchCondition(
        request.keywordLike(), request.ownerIdEqual(), request.subscriberIdEqual(),
        request.cursor(), request.idAfter(),
        request.limit(), request.sortBy(), request.sortDirection());
  }

  private String toCursor(Playlist playlist, PlaylistSortBy sortBy) {
    return switch (sortBy) {
      case UPDATED_AT -> playlist.getUpdatedAt().toString();
      case SUBSCRIBE_COUNT -> String.valueOf(playlist.getSubscriberCount());
    };
  }

  private PlaylistResponse toResponse(Playlist playlist, boolean subscribedByMe) {
    // updatePlaylist 용. contents는 다음 커밋에서 채운다.
    return toResponse(playlist, subscribedByMe, List.of());
  }

  private PlaylistResponse toResponse(
      Playlist playlist, boolean subscribedByMe, List<PlaylistContentData> contents) {
    UserSummary userSummary = userSummaryMapper.toSummary(playlist.getOwner());
    List<PlaylistContentSummary> summaries = contents.stream()
        .map(playlistContentSummaryMapper::toSummary)
        .toList();
    return PlaylistResponse.from(playlist, userSummary, subscribedByMe, summaries);
  }

  private boolean isUniqueViolation(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException
          && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
