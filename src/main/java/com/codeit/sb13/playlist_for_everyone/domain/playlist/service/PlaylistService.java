package com.codeit.sb13.playlist_for_everyone.domain.playlist.service;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import java.util.UUID;

public interface PlaylistService {

  PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request);
  PlaylistResponse getPlaylist(UUID playlistId, UUID ownerId);
}
