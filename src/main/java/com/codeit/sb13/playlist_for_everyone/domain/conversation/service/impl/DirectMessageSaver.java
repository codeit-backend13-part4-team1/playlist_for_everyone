package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 대화방 참여 검증과 DM 한 건의 저장을 독립 트랜잭션으로 묶는다.
 *
 * <p>상위 트랜잭션이 있어도 별도로 커밋하므로 외부 롤백과 이후 실시간 발행 실패로 메시지를 잃지 않는다.
 * 프로필을 포함한 사용자 연관관계는 Reader에서 로딩하지만 URL 생성·Redis 발행은 이곳에서 수행하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageSaver {

    private final DirectMessageRepository directMessageRepository;
    private final ConversationReader conversationReader;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 채널 인가와 별개로 저장 시점에도 참여 여부를 확인한다. 서비스의 직접 호출에도 같은 저장 규칙을 적용한다.
     * 저장 트랜잭션 안에서 메시지 생성 이벤트를 발행하고, 명시적 flush 없이 트랜잭션 종료 시 저장을 확정한다.
     * 커밋 실패는 호출자에게 전달되며, AFTER_COMMIT 후속 처리는 실행되지 않는다.
     *
     * <p>송수신 방향은 정렬된 대화방의 userA/userB 위치가 아니라 인증된 요청자 기준으로 결정한다.
     * 저장 결과에서 이벤트 값을 추출하여 메시지와 이벤트의 ID·송수신자가 일치하도록 한다.
     * 메서드 본문이 끝나면 트랜잭션 프록시가 커밋한다. 커밋 후 DM 본문의 SSE 콜백과 알림 작업 제출이 끝나면
     * Saver 호출이 반환된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DirectMessage save(DirectMessageSendCommand command){
        UUID requesterId = command.requesterId();
        Conversation conversation = conversationReader.getForParticipant(command.conversationId(), requesterId);
        DirectMessage saved = directMessageRepository.save(
                new DirectMessage(
                        new DirectMessageCommand(
                                conversation,
                                conversation.getSender(requesterId),
                                conversation.getReceiver(requesterId),
                                command.content())
                ));

        // 이벤트 등록과 DB 커밋은 별개다. SSE 전달은 리스너의 AFTER_COMMIT 경계에서 시작한다.
        eventPublisher.publishEvent(new DirectMessageCreatedEvent(
                saved.getId(),
                saved.getConversationId(),
                saved.getSenderId(),
                saved.getSenderName(),
                saved.getReceiverId()
        ));

        return saved;
    }
}
