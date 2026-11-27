package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import java.util.UUID;

public record ConnectionKey(UUID userId, Connection connection) {
}
