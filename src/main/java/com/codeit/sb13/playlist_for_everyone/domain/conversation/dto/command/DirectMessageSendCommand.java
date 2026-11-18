package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import java.util.UUID;

/**
 * 송신 흐름에 필요한 대화방·인증된 요청자·본문을 전달한다. 이 record 자체는 입력을 검증하지 않는다.
 * HTTP/STOMP 외 호출자도 입력을 준비해야 하며, 참여 검증과 엔티티 생성 규칙은 저장 과정에 적용된다.
 */
public record DirectMessageSendCommand(UUID conversationId,
                                       UUID requesterId,
                                       String content) {
}
