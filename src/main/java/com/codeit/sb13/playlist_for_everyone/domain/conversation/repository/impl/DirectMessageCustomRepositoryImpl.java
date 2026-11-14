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

/**
 * DM 목록의 정렬·커서 조건과 응답 변환에 필요한 연관 데이터 조회를 QueryDSL로 구현한다.
 * 대화방 미존재·비참여의 예외 결정은 서비스 흐름에서 선행하며, 이 계층은 조회 결과만 반환한다.
 */
@Repository
@RequiredArgsConstructor
public class DirectMessageCustomRepositoryImpl implements DirectMessageCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    /**
     * 발신자·수신자와 각각의 프로필을 fetch join하여 메시지 수에 따른 추가 조회를 방지한다.
     * 프로필이 없어도 메시지가 유지되도록 left join을 사용하고, 다음 페이지 확인용으로 한 건 더 가져온다.
     */
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

    /** 커서 이동이나 페이지 크기에 따라 totalCount가 달라지지 않도록 메시지 범위 조건만 적용한다. */
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

    /**
     * 정렬 방향에 맞춰 생성 시각·ID를 엄격 비교하여 직전 페이지의 마지막 항목을 다시 포함하지 않는다.
     * 같은 시각에는 ID로 순서를 결정하며, 기준 메시지가 삭제되어도 전달된 두 값으로 범위를 계산한다.
     * 커서의 형식과 쌍 검증은 호출 전에 완료되어 있어야 한다.
     */
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

    /** 요청자가 실제 발신자 또는 수신자인 메시지만 포함한다. 대화방 참여 여부의 예외 검증을 대신하지 않는다. */
    private BooleanExpression matchesParticipant(UUID requesterId){
        return matchesRequesterId(directMessage.sender, requesterId)
                .or(matchesRequesterId(directMessage.receiver, requesterId));
    }

    private BooleanExpression matchesRequesterId(QUser user, UUID requesterId){
        return user.id.eq(requesterId);
    }
}
