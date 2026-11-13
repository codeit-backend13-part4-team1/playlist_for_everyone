package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 상대방 기준 조회에서 필수 쿼리 파라미터 userId를 받는다.
 * userId는 상대방 ID이며, 인증된 요청자 ID는 컨트롤러가 별도로 전달해 조회 조건을 만든다.
 * 자기 자신을 지정했는지는 서비스에서 검증한다.
 */
public record ConversationWithUserRequest(
        @NotNull
        UUID userId) {

        public ConversationFindCondition toConversationFindCondition(UUID requesterId){
                return new ConversationFindCondition(requesterId, userId);
        }
}
