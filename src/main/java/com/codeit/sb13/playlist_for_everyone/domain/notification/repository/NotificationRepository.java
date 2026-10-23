package com.codeit.sb13.playlist_for_everyone.domain.notification.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification , UUID> , NotificationCustomRepository{

    void deleteByIdAndReceiver(UUID id, UUID receiverId);
}
