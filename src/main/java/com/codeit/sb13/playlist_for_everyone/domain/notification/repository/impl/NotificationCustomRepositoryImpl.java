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

import static com.codeit.sb13.playlist_for_everyone.domain.notification.entity.QNotification.notification;

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

    private LocalDateTime parseCursorDateTime(NotificationSearchCondition condition) {
        try {
            return LocalDateTime.parse(condition.cursor());
        }catch (DateTimeParseException e){
            throw new NotificationInvalidCursorException(e);
        }
    }

    private BooleanExpression generateReceiverFilter(NotificationSearchCondition condition){
        return notification.receiver.eq(condition.receiverId());
    }

}
