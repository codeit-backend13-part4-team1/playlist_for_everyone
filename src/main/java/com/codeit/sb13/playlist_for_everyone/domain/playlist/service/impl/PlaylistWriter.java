package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플레이리스트 수정을 하나의 쓰기 트랜잭션으로 묶는다.
 * 응답 조회와 변환은 이 트랜잭션이 끝난 뒤 서비스가 PlaylistReader로 수행한다.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistWriter {

  private final PlaylistRepository playlistRepository;

  public void update(UUID playlistId, PlaylistUpdateRequest request) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    playlist.update(request.title(), request.description());
  }
}