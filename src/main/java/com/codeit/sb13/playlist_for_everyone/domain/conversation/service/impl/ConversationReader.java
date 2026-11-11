package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationReader {

    private final ConversationRepository conversationRepository;

    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findExistingConversation(ConversationCreateCommand command){
        UUID userAId = command.requesterId();
        UUID userBId = command.withUserId();
        if (userAId.toString().compareTo(userBId.toString()) > 0) {
            UUID temp = userAId;
            userAId = userBId;
            userBId = temp;
        }
        return conversationRepository.findDetailByUserPair(userAId, userBId, command.requesterId());
    }
}
