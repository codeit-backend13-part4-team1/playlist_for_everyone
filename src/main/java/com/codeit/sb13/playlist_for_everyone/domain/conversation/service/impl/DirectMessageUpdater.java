package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DirectMessageUpdater {

    private final DirectMessageRepository directMessageRepository;

    @PreAuthorize("@directMessageGuard.canAccessDirectMessage("
            + "#command.directMessageId(), #command.conversationId(), #command.requesterId())")
    @Transactional
    public void read(DirectMessageReadCommand command){
        validateDirectMessageExists(command.directMessageId(), command.conversationId());

        directMessageRepository.updateReadStatusUpToMessage(
                command.conversationId(),
                command.requesterId(),
                command.directMessageId(),
                LocalDateTime.now());
    }

    private void validateDirectMessageExists(UUID directMessageId, UUID conversationId) {
        boolean exists = directMessageRepository.existsByIdAndConversationId(
                directMessageId,
                conversationId
        );
        if (exists) return;

        throw new DirectMessageNotFoundException(Map.of(
                "conversationId", conversationId,
                "directMessageId", directMessageId
        ));

    }
}
