package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 대화 상대의 ID만 받는 생성 요청이다. 요청자 ID는 컨트롤러가 인증 정보에서 제공한다.
 * 필수 값은 Bean Validation으로 검사하고, 자기 대화 여부는 서비스에서 검사한다.
 */
public record ConversationCreateRequest(@NotNull UUID withUserId) {

    public ConversationCreateCommand toCommand(UUID requesterId) {
        return new ConversationCreateCommand(requesterId, withUserId);
    }
}
