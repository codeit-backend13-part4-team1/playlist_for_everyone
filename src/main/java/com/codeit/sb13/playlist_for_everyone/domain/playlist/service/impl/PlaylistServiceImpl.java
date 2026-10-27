package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.util.Map;
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
  public CursorResponse<PlaylistResponse> getPlaylists(PlaylistSearchRequest request,
      UUID requesterId) {
    return null;
  }
}
