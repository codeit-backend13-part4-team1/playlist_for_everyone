package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * 메시지 엔티티 생성에 필요한 대화방·발신자·수신자·본문을 묶고 생성 규칙을 검증한다.
 *
 * <p>필수 객체와 공백이 아닌 본문을 요구한다. 발신자와 수신자는 서로 다른 사용자여야 하며,
 * 두 사용자 모두 해당 대화방의 참여자여야 한다. 객체가 달라도 사용자 ID가 같으면 같은 사용자로 본다.
 * 규칙 위반은 IllegalArgumentException으로 거절하고, 유효한 본문은 공백 제거·잘라내기 없이 보존한다.
 * 이 검증은 데이터의 일관성을 위한 것으로, 요청자의 로그인 여부를 확인하는 인증 검증과는 별개다.
 */
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

        // 서로 다른 엔티티 인스턴스나 프록시도 같은 사용자를 가리킬 수 있으므로 ID를 비교한다.
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
