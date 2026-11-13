package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

import java.util.List;

public interface DirectMessageCustomRepository {
    List<DirectMessage> search(DirectMessageSearchCondition condition);

    long searchCount(DirectMessageSearchCondition condition);
}
