package com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationCustomRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationCustomRepositoryImpl implements NotificationCustomRepository {
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<Notification> search(NotificationSearchCondition condition) {
        return List.of();
    }

    @Override
    public long searchCount(NotificationSearchCondition condition) {
        return 0L;
    }
}
