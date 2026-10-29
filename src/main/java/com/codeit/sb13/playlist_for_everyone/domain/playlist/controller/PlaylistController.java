package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
      @AuthenticationPrincipal SecurityUser principal,
      @RequestBody @Valid PlaylistCreateRequest request) {
    PlaylistResponse playlistResponse = playlistService.create(principal.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(playlistResponse);
  }

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistResponse> getPlaylist(
      @PathVariable UUID playlistId,
      @AuthenticationPrincipal SecurityUser principal ){
    PlaylistResponse response = playlistService.getPlaylist(
        playlistId, principal.getUserId());

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<PlaylistResponse>> getPlaylists(
      @Valid PlaylistSearchRequest request,
      @AuthenticationPrincipal SecurityUser principal) {
    CursorResponse<PlaylistResponse> response =
        playlistService.getPlaylists(request, principal.getUserId());
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{playlistId}")
  public ResponseEntity<PlaylistResponse> updatePlaylist(
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request,
      @AuthenticationPrincipal SecurityUser principal) {
    // TODO: 인증 구현이 올라오면 @AuthenticationPrincipal로 교체
    return ResponseEntity.ok(playlistService.updatePlaylist(
        playlistId, request, principal.getUserId()));
  }

  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> deletePlaylist(
      @PathVariable UUID playlistId,
      @AuthenticationPrincipal SecurityUser principal) {
    playlistService.deletePlaylist(playlistId, principal.getUserId());
    return ResponseEntity.ok().build();
  }

}
