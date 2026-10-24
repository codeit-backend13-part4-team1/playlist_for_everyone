package com.codeit.sb13.playlist_for_everyone.domain.notification.entity;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Notification extends CreatedAtEntity {

    @Column(name = "receiver_id",nullable = false)
    private UUID receiver;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationLevel level;

    public Notification (NotificationCommand command){
        this.receiver = command.receiverId();
        this.title = command.title();
        this.content = command.content();
        this.level = command.level();
    }

    public boolean isOwner(UUID receiverId){
        return this.receiver.equals(receiverId);
    }

}
