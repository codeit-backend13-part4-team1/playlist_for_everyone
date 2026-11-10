package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConversationReader {

    private final ConversationRepository conversationRepository;

    public Optional<ConversationDto> findExistingConversation(ConversationCreateCommand command){
        throw new UnsupportedOperationException("기존 대화방 조회는 아직 구현되지 않았습니다.");
    }
}
