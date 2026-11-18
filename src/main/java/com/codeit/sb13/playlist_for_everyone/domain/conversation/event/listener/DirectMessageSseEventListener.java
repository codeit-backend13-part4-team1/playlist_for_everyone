package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageReader;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DM 저장 커밋 후 수신자에게 전달할 SSE를 구성한다.
 *
 * <p>Reader의 독립 읽기 트랜잭션이 종료된 뒤 DTO와 프로필 URL을 만들고 공통 SSE 발행기를 호출한다.
 * 조회·변환·발행 실패를 이 경계에서 처리하여 이미 커밋된 DM과 뒤따르는 WebSocket 발행을 유지한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageSseEventListener {

    private final DirectMessageReader directMessageReader;
    private final DirectMessageMapper directMessageMapper;
    private final SseEventPublisher sseEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handleDirectMessageCreated(DirectMessageCreatedEvent event) {
        try {
            DirectMessage directMessage = directMessageReader.getById(event.directMessageId());
            MessageDto messageDto = directMessageMapper.toMessageDto(directMessage);

            sseEventPublisher.publish(SseEvent.createDirectMessageEvent(
                    event.directMessageId(),
                    event.receiverId(),
                    messageDto
            ));
        } catch (RuntimeException e) {
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
