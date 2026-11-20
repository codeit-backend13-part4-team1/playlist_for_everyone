package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import java.util.UUID;

public record WatchingSessionStartedEvent(
        UUID watcherId,
        String watcherName,
        UUID contentId,
        String contentTitle
) {

}
