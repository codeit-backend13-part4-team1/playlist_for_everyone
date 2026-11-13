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

/**
 * Querydsl로 대화방의 공통 응답 조회와 참여자 제한·검색·커서·집계를 구현한다.
 *
 * <p>최신 메시지 한 건과 전체 메시지를 대상으로 한 검색·미읽음 조건을 별도로 구성한다.
 * 메시지 검색은 EXISTS를 사용하므로 일치하는 메시지 수만큼 대화방 행이 늘어나지 않는다.
 * 실제 HTTP 응답 변환과 페이지 종료 여부 판단은 서비스에 맡긴다.
 */
@Repository
@RequiredArgsConstructor
public class ConversationCustomRepositoryImpl implements ConversationCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<ConversationDetailProjection> findDetailById(ConversationFindByIdCondition condition) {
        UUID requesterId = condition.requesterId();

        // 참여 조건을 쿼리에 포함하여 비참여자에게 대화방 존재 여부를 별도로 노출하지 않는다.
        ConversationDetailResult result = getConversationDetailResult(
                requesterId,
                conversation.id.eq(condition.conversationId()),
                isParticipant(requesterId)
        ).fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<ConversationDetailProjection> findDetailByUserPair(ConversationPair pair) {

        // 호출자가 요청자·상대방으로 구성한 쌍이며, ConversationPair의 정렬 순서는 저장 순서와 같다.
        ConversationDetailResult result = getConversationDetailResult(
                pair.requesterId(),
                conversation.userA.id.eq(pair.userAId()),
                conversation.userB.id.eq(pair.userBId())
        ).fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 본인 참여·검색·커서 조건을 모두 적용한 뒤 대화방 생성 시각과 ID를 같은 방향으로 정렬한다.
     * 다음 페이지 존재 여부를 알 수 있도록 요청한 개수보다 한 건 더 조회한다.
     */
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

    /**
     * 목록과 동일한 참여자·검색 조건으로 집계하되 커서 조건은 제외한다.
     * totalCount가 다음 페이지에 남은 개수가 아니라 전체 검색 결과 수를 나타내도록 한다.
     */
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

    /**
     * 대화방·최신 메시지·요청자 미읽음 여부를 생성자 조회 결과로 묶는 공통 쿼리를 조립한다.
     * 여기서는 실행하지 않고, 호출자가 정렬·limit을 추가한 뒤 fetch 또는 fetchOne으로 실행한다.
     *
     * <p>두 참여자와 프로필 이미지 정보를 미리 조회해 응답 변환 중 추가 조회를 방지한다.
     * 메시지·프로필 이미지가 없는 대화방도 유지하도록 LEFT JOIN을 사용하며,
     * 최신 메시지 선택 조건은 WHERE가 아닌 메시지 JOIN의 ON에 둔다.
     */
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

    /**
     * 같은 대화방에서 더 최신인 메시지가 없는 행만 남긴다.
     * 생성 시각이 같으면 큰 ID를 최신으로 보며, 목록의 정렬 방향이나 검색어와는 독립적이다.
     */
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

    /** 최신 메시지의 상태와 관계없이 요청자가 수신한 전체 메시지 중 미읽음이 하나라도 있는지 계산한다. */
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

    /**
     * 공백 검색어는 조건을 생략하고, 나머지는 앞뒤 공백을 제거해 상대방 이름 또는 전체 메시지에서 찾는다.
     * containsIgnoreCase의 부분 검색을 사용하며, 입력한 %와 _도 와일드카드가 아닌 문자로 검색한다.
     */
    private BooleanExpression matchesKeyword(UUID requesterId, String keywordLike){
        if (!StringUtils.hasText(keywordLike)){
            return null;
        }

        String keyword = keywordLike.trim();

        return buildUserKeywordPredicate(conversation.userA, conversation.userB, requesterId, keyword)
                .or(buildUserKeywordPredicate(conversation.userB, conversation.userA, requesterId, keyword))
                .or(buildMessageKeywordPredicate(keyword));

    }

    /** 요청자가 첫 번째 사용자일 때 두 번째 사용자의 이름을 검색한다. 양방향 조건을 조합해 본인 이름은 제외한다. */
    private BooleanExpression buildUserKeywordPredicate(QUser userA , QUser userB, UUID requesterId, String keyword){
        return userA.id.eq(requesterId)
                .and(userB.name.containsIgnoreCase(keyword));
    }

    /**
     * 최신 메시지 JOIN과 별도의 EXISTS로 과거 메시지까지 검색한다.
     * 여러 메시지가 일치해도 대화방은 한 행으로 유지되므로 페이지 결과와 전체 개수가 중복되지 않는다.
     */
    private BooleanExpression buildMessageKeywordPredicate(String keyword){
        QDirectMessage keywordMessage = new QDirectMessage("keywordMessage");

        return JPAExpressions.selectOne()
                .from(keywordMessage)
                .where(
                        keywordMessage.conversation.eq(conversation),
                        keywordMessage.content.containsIgnoreCase(keyword)
                ).exists();
    }

    /**
     * 생성 시각을 먼저 비교하고, 같으면 ID를 비교하여 정렬 방향상 커서 다음 항목만 남긴다.
     *
     * <p>요청 계층에서 커서 형식과 두 값의 동시 전달을 검증했다는 전제다.
     * DB에서 커서 기준 행을 다시 읽지 않으므로 해당 행이 삭제되어도 전달받은 값으로 조회할 수 있다.
     */
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
