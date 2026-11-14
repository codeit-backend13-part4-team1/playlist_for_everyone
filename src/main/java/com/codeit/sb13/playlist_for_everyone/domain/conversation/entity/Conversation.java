package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 정렬된 두 사용자로 식별하는 1:1 대화방이다.
 *
 * <p>userA와 userB는 저장 순서를 나타내며 요청자와 상대방을 고정해서 의미하지 않는다.
 * ConversationCommand의 정렬과 DB의 사용자 쌍 UNIQUE를 함께 적용해 역방향 요청도 같은 관계로 취급한다.
 * UNIQUE만으로는 순서가 뒤집힌 중복을 막을 수 없으며, 자기 자신과의 대화는 서비스에서 거절한다.
 */
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_conversations_user_pair",
                columnNames = {"user_a_id", "user_b_id"}
        )
)
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Conversation extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    private Conversation(User userA, User userB) {
        this.userA = userA;
        this.userB = userB;
    }

    public Conversation(ConversationCommand command){
       this(command.userA(), command.userB());
    }

    public UUID getUserAId(){
        if (userA == null) {
            return null;
        }
        return userA.getId();
    }
    public UUID getUserBId(){
        if (userB == null) {
            return null;
        }
        return userB.getId();
    }

    public boolean isParticipant(UUID userId){
        if (userA == null || userB == null) {
            return false;
        }

        return userA.getId().equals(userId) || userB.getId().equals(userId);
    }
}
