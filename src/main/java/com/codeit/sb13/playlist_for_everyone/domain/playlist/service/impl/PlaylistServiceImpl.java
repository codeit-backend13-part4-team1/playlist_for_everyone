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
/**
 * 플레이리스트 서비스 구현체.
 *
 * <p>응답을 만드는 메서드(create, getPlaylist, getPlaylists, updatePlaylist)는 DB 처리를
 * {@link PlaylistReader}, {@link PlaylistWriter}의 트랜잭션에 맡기고, 소유자 프로필 이미지 URL과
 * 콘텐츠 썸네일 URL은 그 트랜잭션이 끝난 뒤 이 클래스에서 만든다.
 * 이 클래스에는 클래스 단위 트랜잭션을 두지 않고, 메서드마다 필요한 전파 옵션을 명시한다.
 *
 * <p>create·getPlaylist·getPlaylists·updatePlaylist는 서비스가 스스로 트랜잭션을 열지 않는다.
 * 호출자 트랜잭션이 없으면 URL 생성은 DB 트랜잭션 밖에서 이뤄진다.
 * (create는 호출자 트랜잭션이 있으면 참여한다.)
 */
@Service
@RequiredArgsConstructor
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
  private final PlaylistWriter playlistWriter;

  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  @Override
  @Transactional(propagation = Propagation.SUPPORTS)
  public PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request) {
    // 호출자 트랜잭션이 없으면 트랜잭션 없이 실행되어, 저장·이벤트 발행은 Writer의 트랜잭션에서 끝나고
    // 프로필 이미지 URL 생성은 커밋 이후에 한다. 호출자 트랜잭션이 있으면 그 트랜잭션에 참여한다.
    Playlist saved = playlistWriter.create(ownerId, request);

    // 방금 생성된 플레이리스트라 구독 정보와 콘텐츠가 없으므로 구독 여부는 false, 콘텐츠는 빈 목록이다.
    return toResponse(saved, false, List.of());
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
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PlaylistResponse updatePlaylist(
      UUID playlistId, PlaylistUpdateRequest request, UUID requesterId) {

    playlistWriter.update(playlistId, request);

    PlaylistReader.PlaylistDetail detail = playlistReader.readOne(playlistId, requesterId);
    return toResponse(detail.playlist(), detail.subscribedByMe(), detail.contents());
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
  @Transactional(readOnly = true)
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
