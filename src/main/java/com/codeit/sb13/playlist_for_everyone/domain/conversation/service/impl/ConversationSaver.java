package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationSaver {

    private final ConversationRepository conversationRepository;
    private final ConversationUserReader conversationUserReader;
    private final UserSummaryMapper userSummaryMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationDto save(ConversationCreateCommand command){
        User requester = conversationUserReader.getById(command.requesterId());
        User otherUser = getTargetUser(command.withUserId());
        Conversation saved = conversationRepository.save(
                new Conversation(new ConversationCommand(requester, otherUser)));

        return new ConversationDto(
                saved.getId(),
                userSummaryMapper.toSummary(otherUser),
                null,
                false
        );
    }

    private User getTargetUser(UUID withUserId){
        try {
            return conversationUserReader.getById(withUserId);
        } catch (UserNotFoundException exception){
            throw new ConversationTargetNotFoundException(Map.of("withUserId", withUserId));
        }
    }


}
