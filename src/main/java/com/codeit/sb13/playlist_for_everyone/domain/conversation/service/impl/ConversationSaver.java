package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConversationSaver {

    private final ConversationRepository conversationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationDto save(ConversationCreateCommand command){
        throw new UnsupportedOperationException( "대화방 저장은 아직 구현되지 않았습니다.");
    }


}
