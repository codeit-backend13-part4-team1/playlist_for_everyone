package com.codeit.sb13.playlist_for_everyone.domain.playlist.security;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaylistGuard {

  private final PlaylistRepository playlistRepository;

  public boolean canModify(UUID playlistId, UUID requesterId) {
    return playlistRepository.findById(playlistId)
        .map(playlist -> playlist.isOwnedBy(requesterId))
        .orElse(true);
  }
}