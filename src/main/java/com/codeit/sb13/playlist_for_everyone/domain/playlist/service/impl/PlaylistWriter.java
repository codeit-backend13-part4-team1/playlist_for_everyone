package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플레이리스트 생성·수정을 하나의 쓰기 트랜잭션으로 묶는다.
 * 응답 조회와 변환(프로필 이미지 URL 생성 포함)은 이 트랜잭션이 끝난 뒤 서비스가 수행한다.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class PlaylistWriter {

  private final PlaylistRepository playlistRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 플레이리스트를 저장하고 생성 이벤트를 발행한다.
   * 소유자는 프로필 이미지와 함께 조회하므로, 반환된 플레이리스트의 소유자는
   * 트랜잭션이 끝난 뒤에도 프로필 이미지 Key를 읽을 수 있다.
   */
  public Playlist create(UUID ownerId, PlaylistCreateRequest request) {
    User owner = userRepository.findWithProfileImageById(ownerId)
        .orElseThrow(() -> new UserNotFoundException(Map.of("userId", ownerId)));

    Playlist saved = playlistRepository.save(
        new Playlist(owner, request.title(), request.description()));

    eventPublisher.publishEvent(new PlaylistCreatedEvent(
        saved.getId(),
        saved.getTitle(),
        owner.getId(),
        owner.getName()));

    return saved;
  }

  public void update(UUID playlistId, PlaylistUpdateRequest request) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    playlist.update(request.title(), request.description());
  }
}