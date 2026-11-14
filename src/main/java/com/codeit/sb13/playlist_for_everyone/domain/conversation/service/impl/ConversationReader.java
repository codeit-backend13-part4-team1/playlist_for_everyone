package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.*;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 생성용 기존 대화방 확인, 대화방 조회와 참여 검증의 읽기 트랜잭션을 담당한다.
 *
 * <p>기본 전파 방식인 REQUIRED를 사용한다. 상위 트랜잭션이 없으면 읽기 전용 트랜잭션을
 * 시작하고, 있으면 참여한다. 매번 독립적인 새 트랜잭션을 만드는 Saver와 역할이 다르다.
 *
 * <p>일반 단건 조회는 빈 Optional을 반환하고 서비스가 미존재 예외와 HTTP 응답 변환을 결정한다.
 * validateParticipant는 검증 자체가 역할이므로 미존재·비참여 예외를 이곳에서 직접 결정한다.
 * DB 조회 실패는 원래 예외를 그대로 전달한다.
 */
@Component
@RequiredArgsConstructor
public class ConversationReader {

    private final ConversationRepository conversationRepository;

    /**
     * 생성 요청의 두 ID를 ConversationPair로 정렬하여 이미 존재하는 대화방을 확인한다.
     * 빈 결과를 받았을 때 신규 저장을 시도할지는 호출한 서비스가 결정한다.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findExistingConversation(ConversationCreateCommand command){
        return findConversationDetail(command.toConversationPair());
    }

    /**
     * 상대방 기준 조회도 생성 시 확인과 동일한 사용자 쌍 조회를 사용한다.
     * 정렬 후에도 requesterId를 유지하여 응답용 미읽음 여부를 요청자 기준으로 계산한다.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findExistingConversation(ConversationFindCondition condition) {
       return findConversationDetail(condition.toConversationPair());
    }

    /**
     * 참여자 조건을 포함한 단건 조회를 위임한다. 미존재와 비참여 모두 빈 Optional로 반환된다.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDetailProjection> findById(ConversationFindByIdCondition condition) {
        return conversationRepository.findDetailById(condition);
    }

    /**
     * 본인 참여·검색·커서 조건으로 최대 limit + 1건을 조회한다.
     * 다음 페이지 판단과 초과 항목 제거는 서비스에서 수행한다.
     */
    @Transactional(readOnly = true)
    public List<ConversationDetailProjection> search(ConversationSearchCondition condition) {
        return conversationRepository.search(condition);
    }

    /**
     * 본인 참여·검색 조건만 적용한 전체 개수를 조회한다. 페이지 커서와 limit은 개수에 반영하지 않는다.
     */
    @Transactional(readOnly = true)
    public long searchCount(ConversationSearchCondition condition) {
        return conversationRepository.searchCount(condition);
    }

    /**
     * 대화방을 조회하여 사용자 ID가 두 참여자 중 하나인지 확인한다.
     * 미존재와 비참여 모두 같은 대화방 미존재 예외를 던지고, 비참여 원인은 내부 예외로 남긴다.
     */
    @Transactional(readOnly = true)
    public void validateParticipant(UUID conversationId, UUID requesterId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(Map.of("conversationId", conversationId)));

        if (!conversation.isParticipant(requesterId)) {
           throw new ConversationNotFoundException(Map.of("conversationId", conversationId),
                   new ConversationTargetNotFoundException(Map.of("userId", requesterId)));
        }
    }

    private Optional<ConversationDetailProjection> findConversationDetail(ConversationPair pair) {
        return conversationRepository.findDetailByUserPair(pair);
    }

}
