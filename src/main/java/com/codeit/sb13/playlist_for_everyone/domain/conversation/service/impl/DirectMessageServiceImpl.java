package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectMessageServiceImpl implements DirectMessageService {

    private final DirectMessageReader directMessageReader;
    private final DirectMessageMapper directMessageMapper;
    private final DirectMessageUpdater directMessageUpdater;
    private final ConversationService conversationService;

    @Override
    public void read(DirectMessageReadCommand command) {
        conversationService.validateParticipant(command.conversationId(), command.requesterId());

        directMessageUpdater.read(command);
    }

    @Override
    public CursorResponse<MessageDto> search(DirectMessageSearchCondition condition) {
        conversationService.validateParticipant(condition.conversationId(), condition.requesterId());

        List<DirectMessage> result = directMessageReader.search(condition);
        long totalCount = directMessageReader.searchCount(condition);

        boolean hasNext = result.size() > condition.limit();
        List<DirectMessage> page = hasNext ? result.subList(0, condition.limit()) : result;

        String cursor = null;
        UUID idAfter = null;
        if (hasNext){
            DirectMessage last = page.get(page.size() - 1);
            cursor = last.getCreatedAt().toString();
            idAfter = last.getId();
        }
        List<MessageDto> dtos = page.stream()
                .map(directMessageMapper::toMessageDto)
                .toList();

        return new CursorResponse<>(
                dtos,
                cursor,
                idAfter,
                hasNext,
                totalCount,
                condition.sortBy().getSortKey(),
                condition.sortDirection()
        );
    }
}
