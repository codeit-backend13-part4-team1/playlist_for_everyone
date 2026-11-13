package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConversationWithUserRequest(
        @NotNull
        UUID userId) {
}
