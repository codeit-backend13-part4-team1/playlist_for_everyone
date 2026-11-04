package com.codeit.sb13.playlist_for_everyone.domain.notification.entity;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 수신자에게 보여 줄 알림 내용을 저장한다. ID와 생성시각은 CreatedAtEntity에서 상속한다.
 *
 * <p>receiver는 사용자 엔티티 연관관계가 아닌 수신자의 UUID이며 DB의 receiver_id에 대응한다.
 * 읽음 처리는 서비스가 이 엔티티의 수신자를 확인한 뒤 행을 삭제하는 방식이며, 읽음 여부 필드는 두지 않는다.
 */
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

    /** 서비스에서 전달받은 사용자 ID가 이 알림의 수신자와 일치하는지 확인한다. */
    public boolean isOwner(UUID receiverId){
        return this.receiver.equals(receiverId);
    }

}
