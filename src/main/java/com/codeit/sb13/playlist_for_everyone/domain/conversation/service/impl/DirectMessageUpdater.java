package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DirectMessageUpdater {

    private final DirectMessageRepository directMessageRepository;

    @Transactional
    public void read(DirectMessageReadCommand command){
        directMessageRepository.updateReadStatusUpToMessage(
                command.conversationId(),
                command.requesterId(),
                command.directMessageId(),
                LocalDateTime.now());
    }
}
