package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playlists")
public class PlaylistController {

  private final PlaylistService playlistService;

  @PostMapping
  public ResponseEntity<PlaylistResponse> create(
      // TODO: 인증 구현이 올라오면 @AuthenticationPrincipal로 교체
      @RequestHeader("X-User-Id") UUID ownerId,
      @RequestBody @Valid PlaylistCreateRequest request) {
    PlaylistResponse playlistResponse = playlistService.create(ownerId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(playlistResponse);
  }

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistResponse> getPlaylist(
      @PathVariable UUID playlistId,
      // TODO: 인증 구현이 올라오면 @AuthenticationPrincipal로 교체
      @RequestHeader("X-User-Id") UUID requesterId ){
    PlaylistResponse response = playlistService.getPlaylist(playlistId, requesterId);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<PlaylistResponse>> getPlaylists(
      @Valid PlaylistSearchRequest request,
      // TODO: 인증 구현이 올라오면 @AuthenticationPrincipal로 교체
      @RequestHeader("X-User-Id") UUID requesterId) {
    return null;
  }

}
