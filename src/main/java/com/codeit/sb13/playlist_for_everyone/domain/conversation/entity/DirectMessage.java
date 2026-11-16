package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 1:1 대화방에 저장하는 메시지로, DM 목록 조회와 누적 읽음 처리의 대상이다.
 *
 * <p>sender와 receiver는 실제 발신자와 수신자이며, read는 수신자의 읽음 상태를 뜻한다.
 * 신규 객체는 미읽음 상태로 생성한다. 생성 입력의 일관성은 DirectMessageCommand에서 검증하고,
 * 누적 읽음 변경은 Repository의 벌크 갱신으로 처리한다.
 *
 * <p>대화방 조회의 최신 메시지·미읽음 여부 계산과 전체 메시지 본문 검색에도 사용한다.
 * 이 엔티티 자체가 메시지를 전송하거나 실시간 이벤트를 발행하지는 않는다.
 */
@Table(name = "direct_messages")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DirectMessage extends UpdatedAtEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    /** 검증된 생성 입력을 옮기고 읽음 상태를 false로 초기화한다. null Command는 거절한다. */
    public DirectMessage(DirectMessageCommand command){
        if (command == null) {
            throw new IllegalArgumentException("메시지 생성 입력은 필수입니다.");
        }
        this.conversation = command.conversation();
        this.sender = command.sender();
        this.receiver = command.receiver();
        this.content = command.content();
        this.read = false;
    }

    /**
     * 사용자 ID가 실제 발신자 또는 수신자와 일치하는지 판단한다.
     * Guard의 메시지 접근 검사에 사용하며, 대화방 소속 검증이나 읽음 변경은 수행하지 않는다.
     */
    public boolean isAccess(UUID userId){
        if (sender == null || receiver == null) {
            return false;
        }
        return sender.getId().equals(userId) || receiver.getId().equals(userId);
    }
}
