package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageReader;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DirectMessageSseEventListener {

    private final DirectMessageReader directMessageReader;
    private final DirectMessageMapper directMessageMapper;
    private final SseEventPublisher sseEventPublisher;

    @TransactionalEventListener
    public void handleDirectMessageCreated(DirectMessageCreatedEvent event){

    }
}
