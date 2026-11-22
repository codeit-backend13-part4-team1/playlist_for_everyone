package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.RevokeEvent;
import org.springframework.stereotype.Component;

@Component
public class RedisRevokeEventPublisher {

    public void publish(RevokeEvent<?> event) {
    }
}
