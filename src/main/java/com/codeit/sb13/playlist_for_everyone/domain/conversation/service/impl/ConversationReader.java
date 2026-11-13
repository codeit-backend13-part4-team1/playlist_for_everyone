package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.*;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        return findConversationDetail(command.toConversationPair());
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findExistingConversation(ConversationFindCondition condition) {
       return findConversationDetail(condition.toConversationPair());
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findById(ConversationFindByIdCondition condition) {
        return conversationRepository.findDetailById(condition);
    }

    @Transactional(readOnly = true)
    public List<ConversationDetailProjection> search(ConversationSearchCondition condition) {
        return conversationRepository.search(condition);
    }

    @Transactional(readOnly = true)
    public long searchCount(ConversationSearchCondition condition) {
        return conversationRepository.searchCount(condition);
    }

    private Optional<ConversationDetailProjection> findConversationDetail(ConversationPair pair) {
        return conversationRepository.findDetailByUserPair(pair);
    }

}
