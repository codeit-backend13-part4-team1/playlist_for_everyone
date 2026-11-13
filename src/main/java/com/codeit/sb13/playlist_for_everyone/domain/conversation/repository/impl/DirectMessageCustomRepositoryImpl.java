package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageCustomRepository;

import java.util.List;

public class DirectMessageCustomRepositoryImpl implements DirectMessageCustomRepository {
    @Override
    public List<DirectMessage> search(DirectMessageSearchCondition condition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long searchCount(DirectMessageSearchCondition condition) {
        throw new UnsupportedOperationException();
    }
}
