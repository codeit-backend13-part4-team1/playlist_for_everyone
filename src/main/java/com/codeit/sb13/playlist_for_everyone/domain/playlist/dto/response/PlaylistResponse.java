package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlaylistResponse(
    UUID playlistId,
    UserSummary owner,
    String title,
    String description,
    LocalDateTime updatedAt,
    long subscriberCount,
    boolean subscribedByMe
) {

}
