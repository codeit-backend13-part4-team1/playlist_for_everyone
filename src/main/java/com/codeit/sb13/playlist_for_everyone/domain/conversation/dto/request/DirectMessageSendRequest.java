package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** DM SEND 본문 계약. 내용만 받고, 대화방은 목적지에서·요청자는 인증 정보에서 결합한다. */
public record DirectMessageSendRequest(@NotBlank String content) {

    public DirectMessageSendCommand toCommand(UUID conversationId, UUID requesterId) {
        return new DirectMessageSendCommand(conversationId, requesterId, content);
    }
}
