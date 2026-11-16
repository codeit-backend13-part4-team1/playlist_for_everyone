package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record DirectMessageSendRequest(@NotBlank String content) {

    public DirectMessageSendCommand toCommand(UUID conversationId, UUID requesterId) {
        return new DirectMessageSendCommand(conversationId, requesterId, content);
    }
}
