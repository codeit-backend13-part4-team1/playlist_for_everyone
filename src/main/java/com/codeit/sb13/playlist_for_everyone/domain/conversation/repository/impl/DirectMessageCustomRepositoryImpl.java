package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageCustomRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.QUser;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.QDirectMessage.directMessage;

@Repository
@RequiredArgsConstructor
public class DirectMessageCustomRepositoryImpl implements DirectMessageCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;
    @Override
    public List<DirectMessage> search(DirectMessageSearchCondition condition) {
        return jpaQueryFactory
                .select(directMessage)
                .from(directMessage)
                .leftJoin(directMessage.sender).fetchJoin()
                .leftJoin(directMessage.sender.profileImage).fetchJoin()
                .leftJoin(directMessage.receiver).fetchJoin()
                .leftJoin(directMessage.receiver.profileImage).fetchJoin()
                .where(
                        directMessage.conversation.id.eq(condition.conversationId()),
                        matchesParticipant(condition.requesterId()),
                        buildCursorCondition(condition)
                ).orderBy(
                        condition.isDesc() ? directMessage.createdAt.desc() : directMessage.createdAt.asc(),
                        condition.isDesc() ? directMessage.id.desc() : directMessage.id.asc()
                ).limit(condition.limit() + 1L)
                .fetch();
    }

    @Override
    public long searchCount(DirectMessageSearchCondition condition) {
        Long totalCount = jpaQueryFactory.select(directMessage.count())
                .from(directMessage)
                .where(
                        directMessage.conversation.id.eq(condition.conversationId()),
                        matchesParticipant(condition.requesterId())
                ).fetchOne();

        return totalCount == null ? 0 : totalCount;
    }

    private BooleanExpression buildCursorCondition(DirectMessageSearchCondition condition){
        if (!condition.isCursorAndIdPresent()){
            return null;
        }

        LocalDateTime cursorCreatedAt = LocalDateTime.parse(condition.cursor());
        if (condition.isDesc()){
            return directMessage.createdAt.lt(cursorCreatedAt)
                    .or(directMessage.createdAt.eq(cursorCreatedAt)
                            .and(directMessage.id.lt(condition.idAfter())));
        }

        return directMessage.createdAt.gt(cursorCreatedAt)
                .or(directMessage.createdAt.eq(cursorCreatedAt)
                        .and(directMessage.id.gt(condition.idAfter())));
    }

    private BooleanExpression matchesParticipant(UUID requesterId){
        return matchesRequesterId(directMessage.sender, requesterId)
                .or(matchesRequesterId(directMessage.receiver, requesterId));
    }

    private BooleanExpression matchesRequesterId(QUser user, UUID requesterId){
        return user.id.eq(requesterId);
    }
}
