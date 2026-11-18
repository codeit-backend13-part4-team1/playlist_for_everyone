package com.codeit.sb13.playlist_for_everyone.domain.playlist.event;

import java.util.UUID;

public record PlaylistSubscribedEvent(
    UUID playlistId,
    String playlistTitle,
    UUID ownerId,
    UUID subscriberId,
    String subscriberName
) {

}
