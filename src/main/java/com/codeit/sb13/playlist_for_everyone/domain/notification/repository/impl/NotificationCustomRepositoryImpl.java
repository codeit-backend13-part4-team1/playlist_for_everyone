package com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationCustomRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.notification.NotificationInvalidCursorException;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.notification.entity.QNotification.notification;

/**
 * 수신자 제한과 생성시각·ID 복합 커서를 적용하는 Querydsl 조회 구현이다.
 *
 * <p>같은 생성시각의 알림도 ID로 순서를 결정하며, 두 정렬 키는 같은 방향을 사용한다.
 * 수신자 조건은 목록과 개수 조회에 모두 적용하되, 페이지 경계는 목록 조회에만 적용한다.
 * receiver.id로 연관 사용자의 식별자를 비교하며, 조회 조건에는 기존처럼 수신자 UUID를 전달한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationCustomRepositoryImpl implements NotificationCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<Notification> search(NotificationSearchCondition condition) {
        return jpaQueryFactory
                .select(notification)
                .from(notification)
                .where(
                        filterNotificationsByCondition(condition),
                        generateReceiverFilter(condition)
                )
                .limit(condition.limit()+1L)
                .orderBy(getSortOrder(condition), getSortOrderId(condition))
                .fetch();
    }

    @Override
    public long searchCount(NotificationSearchCondition condition) {
        Long countResult = jpaQueryFactory
                .select(notification.count())
                .from(notification)
                .where(generateReceiverFilter(condition))
                .fetchOne();

        return countResult == null ? 0 : countResult;
    }

    @Override
    public List<UUID> findExistingIds(UUID receiverId, List<UUID> notificationIds) {
        return List.of();
    }

    private OrderSpecifier<?> getSortOrder(NotificationSearchCondition condition) {
       return switch (condition.sortDirection()) {
            case ASCENDING -> getSortingPath(condition.sortBy()).asc();
            case DESCENDING -> getSortingPath(condition.sortBy()).desc();
        };
    }

    private OrderSpecifier<?> getSortOrderId(NotificationSearchCondition condition) {
        return switch (condition.sortDirection()) {
            case ASCENDING -> notification.id.asc();
            case DESCENDING -> notification.id.desc();
        };
    }

    private DateTimePath<LocalDateTime> getSortingPath(SortBy sortBy) {
        if(sortBy == null) return notification.createdAt;
        return switch (sortBy){
            case CREATED_AT -> notification.createdAt;
        };
    }

    /**
     * 내림차순은 커서보다 작은 시각 또는 같은 시각의 작은 ID를 선택하고, 오름차순은 반대로 선택한다.
     * 커서가 가리키는 행을 다시 찾지 않고 전달된 값으로 비교하므로 그 알림이 삭제되어도 탐색할 수 있다.
     * 불완전한 커서 쌍은 null 조건으로 생략하지만, 수신자 조건은 search에서 별도로 유지한다.
     */
    private BooleanExpression filterNotificationsByCondition(NotificationSearchCondition condition){
        if (!condition.isCursorAndIdPresent()){
            return null;
        }

        LocalDateTime cursor = parseCursorDateTime(condition);
        DateTimePath<LocalDateTime> sortingPath = getSortingPath(condition.sortBy());

        if(condition.isDesc()){
            return sortingPath.lt(cursor)
                    .or(sortingPath.eq(cursor)
                                    .and(notification.id.lt(condition.idAfter())));
        }

        return sortingPath.gt(cursor)
                .or(sortingPath.eq(cursor)
                        .and(notification.id.gt(condition.idAfter())));
    }

    /** HTTP 검증을 거치지 않은 내부 호출도 잘못된 날짜를 전달하면 도메인 예외로 변환한다. */
    private LocalDateTime parseCursorDateTime(NotificationSearchCondition condition) {
        try {
            return LocalDateTime.parse(condition.cursor());
        }catch (DateTimeParseException e){
            throw new NotificationInvalidCursorException(e);
        }
    }

    private BooleanExpression generateReceiverFilter(NotificationSearchCondition condition){
        return notification.receiver.id.eq(condition.receiverId());
    }

}
