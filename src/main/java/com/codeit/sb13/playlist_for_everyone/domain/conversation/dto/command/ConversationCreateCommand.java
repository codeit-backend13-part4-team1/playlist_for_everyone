package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import java.util.UUID;

public record ConversationCreateCommand(UUID requesterId, UUID withUserId) {
}
