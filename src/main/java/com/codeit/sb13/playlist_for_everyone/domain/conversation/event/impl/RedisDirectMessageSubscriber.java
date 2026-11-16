package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class RedisDirectMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        throw new UnsupportedOperationException();
    }
}
