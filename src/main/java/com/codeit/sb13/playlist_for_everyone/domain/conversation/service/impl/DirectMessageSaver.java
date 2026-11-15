package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DirectMessageSaver {

    private final DirectMessageRepository directMessageRepository;
    private final ConversationReader conversationReader;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DirectMessage save(DirectMessageSendCommand command){
        UUID requesterId = command.requesterId();
        Conversation conversation = conversationReader.getForParticipant(command.conversationId(), requesterId);

        return directMessageRepository.save(
                new DirectMessage(
                        new DirectMessageCommand(
                                conversation,
                                conversation.getSender(requesterId),
                                conversation.getReceiver(requesterId),
                                command.content())
                ));

    }
}
