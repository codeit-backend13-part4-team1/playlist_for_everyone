package com.codeit.sb13.playlist_for_everyone.domain.conversation.event;

import java.util.UUID;

/**
 * DM 저장 트랜잭션 안에서 발행하는 생성 사실과 후속 처리 대상이다.
 *
 * <p>발행 시점에는 아직 커밋되지 않았으므로 저장 성공을 전제로 하는 소비자는 AFTER_COMMIT으로 처리한다.
 * 메시지 본문이나 JPA 엔티티를 전달하지 않아 소비자가 발행 측 영속성 컨텍스트에 의존하지 않게 한다.
 * 이 이벤트 자체를 브라우저에 전달하지 않는다.
 *
 * <p>DirectMessageSseEventListener는 메시지 ID로 상세 정보를 조회하여 'direct-messages' SSE의 DTO를 만든다.
 * NotificationDomainEventListener는 receiverId와 senderName 스냅샷으로 공통 알림 생성 요청을 만든다.
 * 알림 문구 구성에는 메시지·발신자 재조회가 필요 없으며, 수신자 조회와 알림 저장은 공통 저장 경로가 담당한다.
 * 두 소비자는 같은 이벤트를 각각 처리하므로 DM SSE 리스너에서 알림을 직접 저장하지 않는다.
 *
 * @param directMessageId 저장한 메시지 ID. DM SSE의 이벤트 ID로도 사용한다
 * @param conversationId 메시지가 속한 대화방 ID
 * @param senderId 대화방 참여 검증을 통과한 실제 발신자 ID
 * @param senderName 이벤트 생성 시 읽은 발신자 이름. 알림 문구에 사용하는 스냅샷이며, DM SSE DTO의 커밋 후 조회값과 구분한다
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
