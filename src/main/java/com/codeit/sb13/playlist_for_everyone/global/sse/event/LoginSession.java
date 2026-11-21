package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import java.util.Objects;
import java.util.UUID;

public record LoginSession(UUID id) {
    public LoginSession {
        Objects.requireNonNull(id);
    }
}
