package com.codeit.sb13.playlist_for_everyone.domain.notification.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;

import java.util.List;

public interface NotificationCustomRepository {

    List<Notification> search(NotificationSearchCondition condition);

    long searchCount(NotificationSearchCondition condition);
}
