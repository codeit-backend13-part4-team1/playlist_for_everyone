package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import java.util.UUID;

public record ConversationFindCondition(UUID requesterId, UUID withUserId) {
}
