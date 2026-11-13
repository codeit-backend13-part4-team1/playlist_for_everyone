package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationCustomRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConversationCustomRepositoryImpl implements ConversationCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<ConversationDetailProjection> findDetailById(ConversationFindByIdCondition condition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ConversationDetailProjection> search(ConversationSearchCondition condition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long searchCount(ConversationSearchCondition condition) {
        throw new UnsupportedOperationException();
    }
}
