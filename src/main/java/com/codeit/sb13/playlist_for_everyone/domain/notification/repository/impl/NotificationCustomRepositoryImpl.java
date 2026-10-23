package com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationCustomRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.exception.notification.NotificationInvalidCursorException;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.codeit.sb13.playlist_for_everyone.domain.notification.entity.QNotification.notification;

@Repository
@RequiredArgsConstructor
public class NotificationCustomRepositoryImpl implements NotificationCustomRepository {
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<Notification> search(NotificationSearchCondition condition) {
        boolean descending = condition.sortDirection() == SortDirection.DESCENDING;
        BooleanExpression cursorFilter = null;
        if (StringUtils.hasText(condition.cursor()) && condition.idAfter() != null) {
            LocalDateTime cursor;
            try {
                cursor = LocalDateTime.parse(condition.cursor());
            } catch (DateTimeParseException e) {
                throw new NotificationInvalidCursorException(e);
            }
            cursorFilter = descending
                    ? notification.createdAt.lt(cursor).or(notification.createdAt.eq(cursor).and(notification.id.lt(condition.idAfter())))
                    : notification.createdAt.gt(cursor).or(notification.createdAt.eq(cursor).and(notification.id.gt(condition.idAfter())));
        }
        return jpaQueryFactory.select(notification).from(notification)
                .where(cursorFilter, notification.receiver.eq(condition.receiverId()))
                .limit(condition.limit() + 1)
                .orderBy(descending ? notification.createdAt.desc() : notification.createdAt.asc(),
                        descending ? notification.id.desc() : notification.id.asc())
                .fetch();
    }

    @Override
    public long searchCount(NotificationSearchCondition condition) {
        Long count = jpaQueryFactory.select(notification.count()).from(notification)
                .where(notification.receiver.eq(condition.receiverId()))
                .fetchOne();
        return count == null ? 0L : count;
    }
}
