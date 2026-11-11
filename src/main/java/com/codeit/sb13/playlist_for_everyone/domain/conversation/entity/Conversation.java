package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

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
}
