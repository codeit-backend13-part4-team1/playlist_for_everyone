package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import java.util.UUID;

public record Connection(UUID id) {
    public static Connection create(){
        return new Connection(UUID.randomUUID());
    }
}
