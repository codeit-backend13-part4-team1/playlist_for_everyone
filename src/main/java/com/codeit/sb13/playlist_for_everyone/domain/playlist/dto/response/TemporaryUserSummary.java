package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response;

import java.util.UUID;

public record TemporaryUserSummary(
    UUID userID,
    String username,
    String profileImageUrl
) {

}
