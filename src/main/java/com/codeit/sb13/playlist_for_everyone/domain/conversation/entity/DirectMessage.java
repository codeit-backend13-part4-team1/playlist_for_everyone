package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대화방의 최신 메시지·미읽음 여부 조회와 전체 메시지 본문 검색에 사용하는 메시지 매핑이다.
 *
 * <p>sender와 receiver는 각각 발신자와 수신자이며, read는 수신자의 읽음 상태를 뜻한다.
 * 검색은 과거 메시지까지 대상으로 하며, 응답에 담을 최신 메시지 선택과 별도로 수행한다.
 * 이 매핑과 대화방 응답 변환은 메시지 송신·읽음 변경 API의 구현까지 포함하지 않는다.
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
}
