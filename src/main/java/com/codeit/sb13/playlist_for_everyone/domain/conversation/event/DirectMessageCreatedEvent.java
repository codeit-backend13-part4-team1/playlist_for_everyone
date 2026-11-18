package com.codeit.sb13.playlist_for_everyone.domain.conversation.event;

import java.util.UUID;

public record DirectMessageCreatedEvent(
        UUID directMessageId,
        UUID conversationId,
        UUID senderId,
        String senderName,
        UUID receiverId
) {
}
