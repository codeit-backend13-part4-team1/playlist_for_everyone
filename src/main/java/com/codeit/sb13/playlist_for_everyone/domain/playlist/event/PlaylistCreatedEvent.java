package com.codeit.sb13.playlist_for_everyone.domain.playlist.event;

import java.util.UUID;

public record PlaylistCreatedEvent(
    UUID playlistId,
    String playlistTitle,
    UUID ownerId,
    String ownerName
) {}
