package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageReader;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageSseEventListener {

    private final DirectMessageReader directMessageReader;
    private final DirectMessageMapper directMessageMapper;
    private final SseEventPublisher sseEventPublisher;

    @TransactionalEventListener
    public void handleDirectMessageCreated(DirectMessageCreatedEvent event){
        try {
            MessageDto messageDto = directMessageMapper.toMessageDto(
                    directMessageReader.getById(event.directMessageId()));

            sseEventPublisher.publish(SseEvent.createDirectMessageEvent(
                    event.directMessageId(),
                    event.receiverId(),
                    messageDto
            ));
        }catch (RuntimeException e){
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            log.warn("DM SSE 처리 실패. directMessageId={}, conversationId={}, receiverId={}, exceptionType={}",
                    event.directMessageId(),
                    event.conversationId(),
                    event.receiverId(),
                    cause.getClass().getSimpleName()
            );
        }
    }
}
