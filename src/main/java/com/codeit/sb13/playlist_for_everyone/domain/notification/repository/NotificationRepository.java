package com.codeit.sb13.playlist_for_everyone.domain.notification.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification , UUID> , NotificationCustomRepository{

    /**
     * ID와 수신자가 함께 일치하는 알림만 삭제한다. receiver는 Notification의 필드 이름이다.
     * 대상이 없으면 삭제 없이 종료하여 타인의 알림을 보존하고 반복 요청을 허용한다.
     */
    void deleteByIdAndReceiver(UUID id, UUID receiverId);
}
