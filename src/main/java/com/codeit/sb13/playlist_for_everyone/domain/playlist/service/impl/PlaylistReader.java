package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.ContentRatingProjection;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentQueryRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플레이리스트 응답에 필요한 DB 조회를 하나의 읽기 트랜잭션으로 묶는다.
 * 프로필·썸네일 URL 같은 응답 변환은 이 트랜잭션이 끝난 뒤 서비스가 수행한다.
 * 그래서 콘텐츠도 엔티티가 아니라 URL이 없는 순수 데이터(PlaylistContentData)로 반환한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistReader {

  private final PlaylistRepository playlistRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final PlaylistContentQueryRepository playlistContentQueryRepository;

  public PlaylistDetail readOne(UUID playlistId, UUID requesterId) {
    Playlist playlist = playlistRepository.findWithOwnerById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    List<PlaylistContentData> contents =
        readContents(List.of(playlistId)).getOrDefault(playlistId, List.of());

    return new PlaylistDetail(playlist, subscribedByMe, contents);
  }

  public PlaylistPage readPage(PlaylistSearchCondition condition, UUID requesterId) {
    List<Playlist> fetched = playlistRepository.search(condition);
    long totalCount = playlistRepository.count(condition);

    boolean hasNext = fetched.size() > condition.limit();
    List<Playlist> page = hasNext
        ? List.copyOf(fetched.subList(0, condition.limit()))
        : fetched;

    List<UUID> playlistIds = page.stream().map(Playlist::getId).toList();
    Set<UUID> subscribedIds = findSubscribedIds(playlistIds, requesterId);
    Map<UUID, List<PlaylistContentData>> contents = readContents(playlistIds);

    return new PlaylistPage(page, hasNext, totalCount, subscribedIds, contents);
  }

  private Set<UUID> findSubscribedIds(List<UUID> playlistIds, UUID requesterId) {
    if (playlistIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        subscriptionRepository.findSubscribedPlaylistIds(requesterId, playlistIds));
  }

  /** 플레이리스트별 콘텐츠를 추가한 순서대로 반환한다. 쿼리 수는 플레이리스트 수와 무관하다. */
  private Map<UUID, List<PlaylistContentData>> readContents(Collection<UUID> playlistIds) {
    if (playlistIds.isEmpty()) {
      return Map.of();
    }

    List<PlaylistContent> links = playlistContentRepository.findAllByPlaylistIds(playlistIds);
    if (links.isEmpty()) {
      return Map.of();
    }

    Set<UUID> contentIds = links.stream()
        .map(PlaylistContent::getContentId)
        .collect(Collectors.toSet());

    Map<UUID, Content> contentById = playlistContentQueryRepository.findAllByIds(contentIds)
        .stream()
        .collect(Collectors.toMap(Content::getId, Function.identity()));
    Map<UUID, ContentRatingProjection> ratingById = playlistContentQueryRepository
        .findRatingsByContentIds(contentIds)
        .stream()
        .collect(Collectors.toMap(ContentRatingProjection::getContentId, Function.identity()));

    Map<UUID, List<PlaylistContentData>> result = new HashMap<>();
    for (PlaylistContent link : links) {
      Content content = contentById.get(link.getContentId());
      if (content == null) {
        continue; // 삭제된 콘텐츠는 건너뛴다.
      }
      result.computeIfAbsent(link.getPlaylistId(), id -> new ArrayList<>())
          .add(toData(content, ratingById.get(content.getId())));
    }
    return result;
  }

  private PlaylistContentData toData(Content content, ContentRatingProjection rating) {
    FileInfo fileInfo = content.getFileInfo();
    // 리뷰가 없는 콘텐츠는 평점 0.0, 리뷰 수 0이다.
    double averageRating = rating == null || rating.getAverageRating() == null
        ? 0.0 : rating.getAverageRating();
    int reviewCount = rating == null || rating.getReviewCount() == null
        ? 0 : Math.toIntExact(rating.getReviewCount());

    return new PlaylistContentData(
        content.getId(),
        content.getType(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        fileInfo == null ? null : fileInfo.getKey(),
        content.getTags().stream().map(ContentTag::getTag).toList(),
        averageRating,
        reviewCount);
  }

  public record PlaylistDetail(
      Playlist playlist,
      boolean subscribedByMe,
      List<PlaylistContentData> contents) {
  }

  public record PlaylistPage(
      List<Playlist> playlists,
      boolean hasNext,
      long totalCount,
      Set<UUID> subscribedIds,
      Map<UUID, List<PlaylistContentData>> contents) {
  }
}