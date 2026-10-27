package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import java.time.LocalDateTime;
import java.util.UUID;

public record PlaylistResponse(
    UUID id,
    UserSummary owner,
    String title,
    String description,
    LocalDateTime updatedAt,
    long subscriberCount,
    boolean subscribedByMe
) {

  public static PlaylistResponse from(Playlist playlist, UserSummary userSummary, boolean subscribedByMe) {
  return new PlaylistResponse(playlist.getId(), userSummary,
      playlist.getTitle(), playlist.getDescription(), playlist.getUpdatedAt(),
      playlist.getSubscriberCount(), subscribedByMe);
  }

}
