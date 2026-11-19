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
 * DM 저장 커밋 후 수신자에게 전달할 'direct-messages' SSE를 구성한다.
 *
 * <p>Reader가 저장 측 연결을 재사용해 상세 조회한 뒤 DTO와 프로필 URL을 만들고 공통 SSE 발행기를 호출한다.
 * 조회를 위해 두 번째 연결을 요구하지 않지만, 외부 호출 동안에도 저장 측 연결은 콜백 종료까지 남을 수 있다.
 * 조회·변환·발행의 RuntimeException을 이 경계에서 처리하여 이미 커밋된 DM과 뒤따르는 WebSocket 발행을 유지한다.
 * 수신자의 접속 여부나 대화방 구독 여부를 검사하지 않고, 연결별 전달과 이력 저장은 공통 SSE 계층에 맡긴다.
 *
 * <p>같은 생성 이벤트를 받는 NotificationDomainEventListener가 공통 알림 요청을 별도로 구성한다.
 * 이 리스너는 알림을 직접 저장하지 않으며, 알림 저장·커밋 후 'notifications' SSE 발행은 알림 도메인이 담당한다.
 *
 * <p>현재 DM SSE와 알림 소비자는 동기식 커밋 후 콜백이다. 커밋 후 콜백 처리가 끝나면 Saver 호출이 반환되고
 * Service의 WebSocket 발행이 이어진다. 두 소비자 사이의 실행 순서에 의존하지 않으며,
 * DM SSE·알림 SSE·WebSocket의 브라우저 도착 순서는 보장하지 않는다. 이 리스너는 실패한 전송을 자동 재시도하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageSseEventListener {

    private final DirectMessageReader directMessageReader;
    private final DirectMessageMapper directMessageMapper;
    private final SseEventPublisher sseEventPublisher;

    /**
     * 저장 트랜잭션이 커밋된 경우에만 처리한다. 롤백되거나 트랜잭션 없이 발행한 이벤트는 처리하지 않는다.
     * 메시지 DTO는 커밋 후 조회 결과로 만들고, 전달 대상은 저장 시 확정한 이벤트의 수신자 ID를 사용한다.
     */
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
            // 직접 감싼 예외가 있으면 그 종류만 남긴다. 본문·예외 메시지·스택 트레이스는 기록하지 않는다.
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
