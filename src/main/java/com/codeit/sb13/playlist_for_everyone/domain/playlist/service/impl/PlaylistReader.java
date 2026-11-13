package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
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
 * 프로필 이미지 URL 같은 응답 변환은 이 트랜잭션이 끝난 뒤 서비스가 수행한다.
 * owner와 프로필 이미지는 fetch join으로 가져오므로 트랜잭션 밖에서도 접근할 수 있다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistReader {

  private final PlaylistRepository playlistRepository;
  private final SubscriptionRepository subscriptionRepository;

  public PlaylistDetail readOne(UUID playlistId, UUID requesterId) {
    Playlist playlist = playlistRepository.findWithOwnerById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    return new PlaylistDetail(playlist, subscribedByMe);
  }

  public PlaylistPage readPage(PlaylistSearchCondition condition, UUID requesterId) {
    List<Playlist> fetched = playlistRepository.search(condition);
    long totalCount = playlistRepository.count(condition);

    boolean hasNext = fetched.size() > condition.limit();
    List<Playlist> page = hasNext
        ? List.copyOf(fetched.subList(0, condition.limit()))
        : fetched;

    Set<UUID> subscribedIds = findSubscribedIds(page, requesterId);
    return new PlaylistPage(page, hasNext, totalCount, subscribedIds);
  }

  private Set<UUID> findSubscribedIds(List<Playlist> playlists, UUID requesterId) {
    if (playlists.isEmpty()) {
      return Set.of();
    }
    List<UUID> playlistIds = playlists.stream().map(Playlist::getId).toList();
    return new HashSet<>(
        subscriptionRepository.findSubscribedPlaylistIds(requesterId, playlistIds));
  }

  public record PlaylistDetail(Playlist playlist, boolean subscribedByMe) {
  }

  public record PlaylistPage(
      List<Playlist> playlists,
      boolean hasNext,
      long totalCount,
      Set<UUID> subscribedIds) {
  }
}