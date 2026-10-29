package com.codeit.sb13.playlist_for_everyone.domain.playlist.service;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import java.util.UUID;

public interface PlaylistService {

  PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request);

  PlaylistResponse getPlaylist(UUID playlistId, UUID requesterId);

  CursorResponse<PlaylistResponse> getPlaylists(PlaylistSearchRequest request, UUID requesterId);

  PlaylistResponse updatePlaylist(UUID playlistId, PlaylistUpdateRequest request, UUID requesterId);

  void deletePlaylist(UUID playlistId, UUID requesterId);

  void addContent(UUID playlistId, UUID contentId, UUID requesterId);

}