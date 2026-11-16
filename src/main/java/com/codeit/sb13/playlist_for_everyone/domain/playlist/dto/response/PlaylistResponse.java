package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


public record PlaylistResponse(
    UUID id,
    UserSummary owner,
    String title,
    String description,
    LocalDateTime updatedAt,
    long subscriberCount,
    boolean subscribedByMe,
    List<PlaylistContentSummary> contents
) {

  public static PlaylistResponse from(
      Playlist playlist,
      UserSummary userSummary,
      boolean subscribedByMe,
      List<PlaylistContentSummary> contents) {
    return new PlaylistResponse(playlist.getId(), userSummary,
        playlist.getTitle(), playlist.getDescription(), playlist.getUpdatedAt(),
        playlist.getSubscriberCount(), subscribedByMe, contents);
  }

}