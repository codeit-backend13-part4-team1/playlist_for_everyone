package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.util.Map;
import java.util.Optional;
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


  @Override
  @Transactional
  public PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request) {
    User user = userRepository.findById(ownerId)
        .orElseThrow(() -> new UserNotFoundException(
            Map.of("userId", ownerId)));

    Playlist playlist = new Playlist(ownerId, request.title(), request.description());

    Playlist saved = playlistRepository.save(playlist);
    UserSummary userSummary = new UserSummary(user.getId(), user.getName(), null);

    return PlaylistResponse.from(saved, userSummary, false );
                          //소유자 본인이 소유한 플레이 리스트는 구독할 수 없으므로 항상 false이다.

  }

  @Override
  public PlaylistResponse getPlaylist(UUID playlistId, UUID requesterId) {

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new PlayListNotFoundException(
            Map.of("playlistId", playlistId)));

    UUID ownerId = playlist.getOwnerId();

    User owner = userRepository.findById(ownerId)
        .orElseThrow(() -> new UserNotFoundException(
            Map.of("userId", ownerId)));

    boolean subscribedByMe = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId);

    UserSummary userSummary = new UserSummary(ownerId, owner.getName(), null);

    return PlaylistResponse.from(playlist, userSummary, subscribedByMe);
  }
}
