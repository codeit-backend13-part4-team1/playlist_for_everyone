package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import java.util.UUID;

public record DirectMessageSendCommand(UUID conversationId,
                                       UUID requesterId,
                                       String content) {
}
