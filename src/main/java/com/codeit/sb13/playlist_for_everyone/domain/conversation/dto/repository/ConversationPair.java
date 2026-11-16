package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;

import java.util.UUID;

/**
 * 생성 확인·상대방 기준 조회에 사용하는 정렬된 사용자 ID 쌍과 요청자 ID다.
 *
 * <p>UUID 표준 문자열의 사전순으로 정렬하여 저장용 ConversationCommand와 같은 A·B 순서를 만든다.
 * requesterId는 정렬하지 않고 유지하므로 요청 방향이 달라도 미읽음 여부를 요청자 기준으로 계산한다.
 *
 * <p>인증, 요청자의 참여 여부, 자기 대화 여부를 검증하는 객체는 아니다.
 * 호출자는 필수 값 검증을 마친 요청자·상대방 ID로 쌍을 구성해야 한다.
 */
public record ConversationPair(UUID userAId, UUID userBId, UUID requesterId) {

    public ConversationPair {
        if (userAId.toString().compareTo(userBId.toString()) > 0) {
            UUID temp = userAId;
            userAId = userBId;
            userBId = temp;
        }
    }
}
