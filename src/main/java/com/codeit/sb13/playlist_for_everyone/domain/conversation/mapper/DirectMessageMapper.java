package com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper;


import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectMessageMapper {

    private final UserSummaryMapper userSummaryMapper;

    public MessageDto toMessageDto(DirectMessage message){
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
