package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;

import java.util.UUID;

public record ConversationDto(UUID id, UserSummary with, MessageDto lastestMessage, boolean hasUnread) {
}
