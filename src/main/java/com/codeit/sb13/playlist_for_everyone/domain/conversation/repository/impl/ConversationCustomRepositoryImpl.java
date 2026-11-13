package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.*;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.QDirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationCustomRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.QConversation.conversation;
import static com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.QDirectMessage.directMessage;

@Repository
@RequiredArgsConstructor
public class ConversationCustomRepositoryImpl implements ConversationCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<ConversationDetailProjection> findDetailById(ConversationFindByIdCondition condition) {
        UUID requesterId = condition.requesterId();

        ConversationDetailResult result = getConversationDetailResult(
                requesterId,
                conversation.id.eq(condition.conversationId()),
                isParticipant(requesterId)
        ).fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<ConversationDetailProjection> findDetailByUserPair(ConversationPair pair) {

        ConversationDetailResult result = getConversationDetailResult(
                pair.requesterId(),
                conversation.userA.id.eq(pair.userAId()),
                conversation.userB.id.eq(pair.userBId())
        ).fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<ConversationDetailProjection> search(ConversationSearchCondition condition) {
        UUID requesterId = condition.requesterId();

        List<ConversationDetailResult> results = getConversationDetailResult(
                requesterId,
                isParticipant(requesterId),
                matchesKeyword(requesterId, condition.keywordLike()),
                buildCursorPredicate(condition))
                .orderBy(
                        condition.isDesc() ? conversation.createdAt.desc() : conversation.createdAt.asc(),
                        condition.isDesc() ? conversation.id.desc() : conversation.id.asc()
                ).limit(condition.limit() + 1L)
                .fetch();
        return new ArrayList<>(results);
    }

    @Override
    public long searchCount(ConversationSearchCondition condition) {
        Long totalCount = jpaQueryFactory
                .select(conversation.count())
                .from(conversation)
                .where(
                        isParticipant(condition.requesterId()),
                        matchesKeyword(condition.requesterId(), condition.keywordLike())
                ).fetchOne();

        return totalCount == null ? 0 : totalCount;
    }

    private JPAQuery<ConversationDetailResult> getConversationDetailResult(UUID requesterId, BooleanExpression ...expressions) {
        return jpaQueryFactory
                .select(Projections.constructor(
                        ConversationDetailResult.class,
                        conversation,
                        directMessage,
                        hasUnreadMessagesForRequester(requesterId)
                ))
                .from(conversation)
                .leftJoin(conversation.userA).fetchJoin()
                .leftJoin(conversation.userA.profileImage).fetchJoin()
                .leftJoin(conversation.userB).fetchJoin()
                .leftJoin(conversation.userB.profileImage).fetchJoin()
                .leftJoin(directMessage).fetchJoin()
                .on(directMessage.conversation.eq(conversation)
                        .and(isLatestMessageCondition()))
                .where(expressions);
    }

    private BooleanExpression isLatestMessageCondition() {
        QDirectMessage newer = new QDirectMessage("newer");
        return JPAExpressions.selectOne()
                .from(newer)
                .where(newer.conversation.eq(conversation),
                        newer.createdAt.gt(directMessage.createdAt)
                                .or(newer.createdAt.eq(directMessage.createdAt)
                                        .and(newer.id.gt(directMessage.id))))
                .notExists();
    }

    private BooleanExpression hasUnreadMessagesForRequester(UUID requesterId) {
        QDirectMessage unRead = new QDirectMessage("unRead");
        return JPAExpressions.selectOne()
                .from(unRead)
                .where(
                        unRead.conversation.eq(conversation),
                        unRead.read.isFalse(),
                        unRead.receiver.id.eq(requesterId))
                .exists();
    }

    private BooleanExpression isParticipant(UUID requesterId){
        return conversation.userA.id.eq(requesterId)
                .or(conversation.userB.id.eq(requesterId));
    }

    private BooleanExpression matchesKeyword(UUID requesterId, String keywordLike){
        if (!StringUtils.hasText(keywordLike)){
            return null;
        }

        String keyword = keywordLike.trim();

        return buildUserKeywordPredicate(conversation.userA, conversation.userB, requesterId, keyword)
                .or(buildUserKeywordPredicate(conversation.userB, conversation.userA, requesterId, keyword))
                .or(buildMessageKeywordPredicate(keyword));

    }

    private BooleanExpression buildUserKeywordPredicate(QUser userA , QUser userB, UUID requesterId, String keyword){
        return userA.id.eq(requesterId)
                .and(userB.name.containsIgnoreCase(keyword));
    }

    private BooleanExpression buildMessageKeywordPredicate(String keyword){
        QDirectMessage keywordMessage = new QDirectMessage("keywordMessage");

        return JPAExpressions.selectOne()
                .from(keywordMessage)
                .where(
                        keywordMessage.conversation.eq(conversation),
                        keywordMessage.content.containsIgnoreCase(keyword)
                ).exists();
    }

    private BooleanExpression buildCursorPredicate(ConversationSearchCondition condition){
        if (!condition.isCursorAndIdPresent()){
            return null;
        }

        LocalDateTime cursorCreatedAt = LocalDateTime.parse(condition.cursor());
        if(condition.isDesc()){
            return conversation.createdAt.lt(cursorCreatedAt)
                    .or(conversation.createdAt.eq(cursorCreatedAt)
                            .and(conversation.id.lt(condition.idAfter())));
        }

        return conversation.createdAt.gt(cursorCreatedAt)
                .or(conversation.createdAt.eq(cursorCreatedAt)
                        .and(conversation.id.gt(condition.idAfter())));
    }


}
