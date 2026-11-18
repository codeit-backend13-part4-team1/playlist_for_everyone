package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;

/**
 * 조회한 사용자 엔티티 두 개를 대화방 저장 순서로 정규화하는 생성용 입력이다.
 *
 * <p>참여자 null을 방어하고 UUID 표준 문자열을 사전순으로 정렬한다.
 * 두 사용자는 ID가 부여된 상태여야 하며, 자기 대화 여부는 서비스에서 먼저 검증한다.
 * 요청 방향을 보존하는 ConversationCreateCommand와 달리 생성 후 A·B는 저장 순서를 의미한다.
 */
public record ConversationCommand(User userA, User userB) {

    public ConversationCommand{
        if (userA == null) {
            throw new IllegalArgumentException("대화방 참여자 A는 필수입니다.");
        }
        if (userB == null) {
            throw new IllegalArgumentException("대화방 참여자 B는 필수입니다.");
        }
        // 조회용 ConversationPair도 같은 문자열 정렬 규칙을 사용하여 저장·조회 시 A·B 순서를 맞춘다.
        String userAId = userA.getId().toString();
        String userBId = userB.getId().toString();
        if (userAId.compareTo(userBId) > 0) {
            User userACopy = userA;
            userA = userB;
            userB = userACopy;
        }
    }
}
