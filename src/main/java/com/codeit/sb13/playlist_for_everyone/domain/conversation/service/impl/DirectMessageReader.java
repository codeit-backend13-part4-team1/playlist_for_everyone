package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DirectMessageReader {

    private final DirectMessageRepository directMessageRepository;

    @Transactional(readOnly = true)
    public List<DirectMessage> search(DirectMessageSearchCondition condition){
        return directMessageRepository.search(condition);
    }

    @Transactional(readOnly = true)
    public long searchCount(DirectMessageSearchCondition condition){
       return directMessageRepository.searchCount(condition);
    }
}
