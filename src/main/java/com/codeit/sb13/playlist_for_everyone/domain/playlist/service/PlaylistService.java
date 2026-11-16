package com.codeit.sb13.playlist_for_everyone.domain.playlist.service;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;

import java.util.List;
import java.util.UUID;

public interface PlaylistService {

  PlaylistResponse create(UUID ownerId, PlaylistCreateRequest request);

  PlaylistResponse getPlaylist(UUID playlistId, UUID requesterId);

  CursorResponse<PlaylistResponse> getPlaylists(PlaylistSearchRequest request, UUID requesterId);

  PlaylistResponse updatePlaylist(UUID playlistId, PlaylistUpdateRequest request, UUID requesterId);

  void deletePlaylist(UUID playlistId, UUID requesterId);

  void addContent(UUID playlistId, UUID contentId, UUID requesterId);

  void removeContent(UUID playlistId, UUID contentId, UUID requesterId);

  void subscribe(UUID playlistId, UUID subscriberId);

  void unsubscribe(UUID playlistId, UUID subscriberId);

  /**
   * 플레이리스트를 구독하는 사용자 ID 목록을 반환한다. 구독 관계가 없으면 빈 목록이다.
   * 다른 도메인에서 사용할 내부 조회이며, 현재 인증 사용자나 공개 HTTP API에 의존하지 않는다.
   * 반환 순서는 보장하지 않는다.
   */
  List<UUID> findSubscriberIds(UUID playlistId);

}
