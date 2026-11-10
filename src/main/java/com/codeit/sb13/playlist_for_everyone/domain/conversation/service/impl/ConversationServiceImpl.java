package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationReader conversationReader;
    private final ConversationSaver conversationSaver;

    @Override
    public ConversationDto create(ConversationCreateCommand createCommand) {
       throw new UnsupportedOperationException("대화방 생성은 아직 구현되지 않았습니다.");
    }
}
