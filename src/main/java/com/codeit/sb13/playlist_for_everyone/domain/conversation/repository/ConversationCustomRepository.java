package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;

import java.util.List;
import java.util.Optional;

public interface ConversationCustomRepository {

    Optional<ConversationDetailProjection> findDetailById(ConversationFindByIdCondition condition);

    List<ConversationDetailProjection> search(ConversationSearchCondition condition);

    long searchCount(ConversationSearchCondition condition);
}
