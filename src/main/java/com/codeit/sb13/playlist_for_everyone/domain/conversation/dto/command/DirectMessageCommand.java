package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

public record DirectMessageCommand (
        Conversation conversation,
        User sender,
        User receiver,
        String content){

    public DirectMessageCommand {

        if (conversation == null) {
            throw new IllegalArgumentException("대화방은 필수입니다.");
        }
        if (sender == null) {
            throw new IllegalArgumentException("발신자는 필수입니다.");
        }
        if (receiver == null) {
            throw new IllegalArgumentException("수신자는 필수입니다.");
        }
        if (!StringUtils.hasText(content)){
            throw new IllegalArgumentException("메시지 본문은 공백일 수 없습니다.");
        }

        UUID senderId = sender.getId();
        UUID receiverId = receiver.getId();
        if (Objects.equals(senderId, receiverId)){
            throw new IllegalArgumentException("발신자와 수신자는 서로 다른 사용자여야 합니다.");
        }

        UUID userAId = conversation.getUserAId();
        UUID userBId = conversation.getUserBId();

        boolean senderIsParticipant = Objects.equals(senderId, userAId)
                || Objects.equals(senderId, userBId);

        boolean receiverIsParticipant = Objects.equals(receiverId, userAId)
                || Objects.equals(receiverId, userBId);

        if (!senderIsParticipant || !receiverIsParticipant) {
            throw new IllegalArgumentException("발신자와 수신자는 해당 대화방의 참여자여야 합니다.");
        }
    }
}
