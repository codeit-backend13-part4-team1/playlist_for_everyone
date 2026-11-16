package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DirectMessageSendRequest(@NotBlank String content) {
}
