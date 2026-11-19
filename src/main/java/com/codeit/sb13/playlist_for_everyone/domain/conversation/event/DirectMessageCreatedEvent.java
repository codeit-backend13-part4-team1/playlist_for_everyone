package com.codeit.sb13.playlist_for_everyone.domain.conversation.event;

import java.util.UUID;

/**
 * DM 저장 트랜잭션 안에서 발행하는 생성 사실과 후속 처리 대상이다.
 *
 * <p>발행 시점에는 아직 커밋되지 않았으므로 저장 성공을 전제로 하는 소비자는 AFTER_COMMIT으로 처리한다.
 * 메시지 본문이나 JPA 엔티티를 전달하지 않아 소비자가 발행 측 영속성 컨텍스트에 의존하지 않게 한다.
 * SSE 소비자는 메시지 ID로 상세 정보를 다시 조회하며, 이 이벤트 자체를 브라우저에 전달하지 않는다.
 *
 * @param directMessageId 저장한 메시지 ID. DM SSE의 이벤트 ID로도 사용한다
 * @param conversationId 메시지가 속한 대화방 ID
 * @param senderId 대화방 참여 검증을 통과한 실제 발신자 ID
 * @param senderName 이벤트 생성 시 읽은 발신자 이름. 후속 소비자가 사용할 스냅샷이다
 * @param receiverId 대화방에서 발신자의 상대방으로 결정한 수신자 ID
 */
public record DirectMessageCreatedEvent(
        UUID directMessageId,
        UUID conversationId,
        UUID senderId,
        String senderName,
        UUID receiverId
) {
}
