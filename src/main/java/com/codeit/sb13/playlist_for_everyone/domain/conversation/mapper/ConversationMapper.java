package com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationMapper {

    private final UserSummaryMapper userSummaryMapper;
    private final DirectMessageMapper messageMapper;

    public ConversationDto toConversationDto(UUID requesterId, Conversation conversation) {
        return toConversationDto(
                conversation,
                false,
                null,
                requesterId
        );
    }

    public ConversationDto toConversationDto(UUID requesterId, ConversationDetailProjection projection) {
        return toConversationDto(
                projection.getConversation(),
                projection.getHasUnread(),
                projection.getLastMessage(),
                requesterId
        );
    }

    private ConversationDto toConversationDto(Conversation conversation, boolean hasUnread, DirectMessage lastMessage, UUID requesterId) {
        return new ConversationDto(
                conversation.getId(),
                toOtherUserSummary(conversation, requesterId),
                messageMapper.toMessageDto(lastMessage),
                hasUnread
        );
    }

    private UserSummary toOtherUserSummary(Conversation conversation, UUID requesterId) {
        if (conversation.getUserAId().equals(requesterId)) {
            return userSummaryMapper.toSummary(conversation.getUserB());
        }
        return userSummaryMapper.toSummary(conversation.getUserA());
    }

}
