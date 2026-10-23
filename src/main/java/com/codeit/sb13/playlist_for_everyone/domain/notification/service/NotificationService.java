package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public CursorResponse<NotificationDto> getNotifications(NotificationSearchCondition condition) {
        throw new UnsupportedOperationException("RED: 알림 페이지 응답 구현 전");
    }

    public void confirm(UUID notificationId, UUID receiverId) {
        // RED: 알림 삭제 서비스 구현 전
    }
}
