package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플레이리스트 응답에 필요한 DB 조회를 하나의 읽기 트랜잭션으로 묶는다.
 * 콘텐츠 요약은 콘텐츠 도메인(ContentService)이 만들기 때문에, 여기서는 플레이리스트별
 * 콘텐츠 id만 추가한 순서대로 돌려준다. URL 생성 같은 응답 변환은 이 트랜잭션이 끝난 뒤
 * 서비스가 수행한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistReader {

  private final PlaylistRepository playlistRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PlaylistContentRepository playlistContentRepository;

  public PlaylistDetail readOne(UUID playlistId, UUID requesterId) {
    Playlist playlist = playlistRepository.findWithOwnerById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    List<UUID> contentIds =
        readContentIds(List.of(playlistId)).getOrDefault(playlistId, List.of());

    return new PlaylistDetail(playlist, subscribedByMe, contentIds);
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
    Map<UUID, List<UUID>> contentIds = readContentIds(playlistIds);

    return new PlaylistPage(page, hasNext, totalCount, subscribedIds, contentIds);
  }

  private Set<UUID> findSubscribedIds(List<UUID> playlistIds, UUID requesterId) {
    if (playlistIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        subscriptionRepository.findSubscribedPlaylistIds(requesterId, playlistIds));
  }

  /** 플레이리스트별 콘텐츠 id를 추가한 순서대로 반환한다. 쿼리 수는 플레이리스트 수와 무관하다. */
  private Map<UUID, List<UUID>> readContentIds(Collection<UUID> playlistIds) {
    if (playlistIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, List<UUID>> result = new HashMap<>();
    for (PlaylistContent link : playlistContentRepository.findAllByPlaylistIds(playlistIds)) {
      result.computeIfAbsent(link.getPlaylistId(), id -> new ArrayList<>())
          .add(link.getContentId());
    }
    return result;
  }

  public record PlaylistDetail(
      Playlist playlist,
      boolean subscribedByMe,
      List<UUID> contentIds) {
  }

  public record PlaylistPage(
      List<Playlist> playlists,
      boolean hasNext,
      long totalCount,
      Set<UUID> subscribedIds,
      Map<UUID, List<UUID>> contentIds) {
  }
}