package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 요청 방향과 무관하게 사용자 쌍으로 기존 대화방과 응답에 필요한 정보를 조회한다.
 *
 * <p>기본 전파 방식인 REQUIRED를 사용한다. 상위 트랜잭션이 없으면 읽기 전용 트랜잭션을
 * 시작하고, 있으면 참여한다. 매번 독립적인 새 트랜잭션을 만드는 Saver와 역할이 다르다.
 */
@Component
@RequiredArgsConstructor
public class ConversationReader {

    private final ConversationRepository conversationRepository;

    /**
     * 대화방이 없으면 빈 Optional을 반환하며, 조회 실패는 호출자에게 그대로 전달한다.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findExistingConversation(ConversationCreateCommand command){
        UUID userAId = command.requesterId();
        UUID userBId = command.withUserId();
        // 저장용 ConversationCommand와 동일한 UUID 문자열 사전순으로 조회해야 한다.
        // 정렬은 조회 키에만 적용하며, 미읽음 판단에 사용할 원래 요청자 ID는 유지한다.
        if (userAId.toString().compareTo(userBId.toString()) > 0) {
            UUID temp = userAId;
            userAId = userBId;
            userBId = temp;
        }
        return conversationRepository.findDetailByUserPair(userAId, userBId, command.requesterId());
    }
}
