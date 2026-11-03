package com.codeit.sb13.playlist_for_everyone.domain.playlist.event;

import java.util.UUID;

public record PlaylistContentAddedEvent(
    UUID playlistId,
    String playlistTitle,
    UUID contentId,
    String contentTitle,
    UUID actorId
) {

}
