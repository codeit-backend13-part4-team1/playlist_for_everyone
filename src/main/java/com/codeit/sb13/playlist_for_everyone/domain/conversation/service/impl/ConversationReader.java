package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationReader {

    private final ConversationRepository conversationRepository;
    private final UserSummaryMapper userSummaryMapper;

    public Optional<ConversationDto> findExistingConversation(ConversationCreateCommand command){
        UUID userAId = command.requesterId();
        UUID userBId = command.withUserId();
        if (userAId.toString().compareTo(userBId.toString()) > 0) {
            UUID temp = userAId;
            userAId = userBId;
            userBId = temp;
        }

        return conversationRepository.findDetailByUserPair(userAId, userBId, command.requesterId())
                .map(projection -> {
                    Conversation conversation = projection.getConversation();
                    return new ConversationDto(
                            conversation.getId(),
                            findRequesterUserSummary(conversation, command.requesterId()),
                            toMessageDto(projection.getLastMessage()),
                            projection.getHasUnread()
                    );
                });
    }

    private UserSummary findRequesterUserSummary(Conversation conversation, UUID requesterId) {
        if (conversation.getUserA().getId().equals(requesterId)) {
            return userSummaryMapper.toSummary(conversation.getUserB());
        }
        return userSummaryMapper.toSummary(conversation.getUserA());
    }

    private MessageDto toMessageDto(DirectMessage message){
        if (message == null){
            return null;
        }
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getCreatedAt(),
                userSummaryMapper.toSummary(message.getSender()),
                userSummaryMapper.toSummary(message.getReceiver()),
                message.getContent()
        );
    }
}
