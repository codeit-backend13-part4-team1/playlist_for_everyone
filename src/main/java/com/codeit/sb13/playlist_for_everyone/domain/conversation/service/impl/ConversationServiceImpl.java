package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.ConversationMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 대화방 생성·조회 흐름에서 자기 대화 검증, 조회·저장 호출, 예외 결정과 응답 변환을 조정한다.
 *
 * <p>이 서비스는 직접 트랜잭션을 시작하지 않는다. 조회는 Reader에, 독립적인 저장 트랜잭션은
 * Saver에 맡겨 저장 실패가 조회·응답 변환 단계까지 같은 트랜잭션으로 묶이지 않도록 한다.
 *
 * <p>상위 트랜잭션이 없는 컨트롤러 호출에서는 조회 또는 저장 트랜잭션이 끝난 뒤 DTO를 변환한다.
 * 상위 트랜잭션이 있으면 조회와 변환은 그 영향을 받지만, Saver의 저장 커밋은 독립적이다.
 * 저장 커밋 이후 프로필 이미지 URL 생성에 실패해도 이미 생성된 대화방은 유지된다.
 *
 * <p>조회 API는 Reader만 사용하며, 목록 응답의 초과 항목 제거와 다음 커서 구성도 이 계층에서 맡는다.
 * 인증 자체는 수행하지 않으므로 호출자가 인증된 요청자 ID를 전달해야 한다.
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationReader conversationReader;
    private final ConversationSaver conversationSaver;
    private final ConversationMapper conversationMapper;

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String CONVERSATION_UNIQUE_CONSTRAINT = "uq_conversations_user_pair";

    @Override
    public ConversationDto create(ConversationCreateCommand createCommand) {
       if (createCommand.requesterId().equals(createCommand.withUserId())){
           throw new SelfConversationNotAllowedException();
       }

       return conversationReader.findExistingConversation(createCommand)
               .map(projection -> conversationMapper.toConversationDto(createCommand.requesterId(), projection))
               .orElseGet(() -> createOrGetConversation(createCommand));
    }

    /**
     * 자기 대화를 먼저 거절한 뒤 기존 사용자 쌍을 조회한다.
     * 사용자 존재 여부를 따로 조회하지 않으며, 조회 결과가 없으면 동일한 대화방 미존재 예외로 처리한다.
     */
    @Override
    public ConversationDto findWithUser(ConversationFindCondition condition) {
        UUID requesterId = condition.requesterId();
        UUID withUserId = condition.withUserId();
        if (requesterId.equals(withUserId)){
            throw new SelfConversationNotAllowedException();
        }

        return conversationReader.findExistingConversation(condition)
                .map(projection -> conversationMapper.toConversationDto(requesterId, projection))
                .orElseThrow(() -> new ConversationNotFoundException(Map.of("withUserId", withUserId)));
    }

    /**
     * Repository에서 대화방 ID와 참여자 조건을 함께 적용한 결과를 응답으로 변환한다.
     * 비참여자의 조회도 빈 결과로 전달되므로 미존재와 구분하지 않고 같은 예외를 반환한다.
     */
    @Override
    public ConversationDto findById(ConversationFindByIdCondition condition) {
        UUID requesterId = condition.requesterId();
        return conversationReader.findById(condition)
                .map(projection -> conversationMapper.toConversationDto(requesterId, projection))
                .orElseThrow(() -> new ConversationNotFoundException(Map.of("conversationId", condition.conversationId())));
    }

    /**
     * 페이지 조회와 전체 개수 조회를 마친 뒤 실제 반환할 항목과 커서를 구성한다.
     *
     * <p>목록과 개수는 별도 Reader 호출이다. 상위 트랜잭션이 없는 일반 HTTP 호출에서는
     * 각각의 읽기 트랜잭션이 종료된 뒤 응답을 변환하며, 두 조회가 같은 시점의 결과임을 보장하지 않는다.
     */
    @Override
    public CursorResponse<ConversationDto> search(ConversationSearchCondition condition) {

        List<ConversationDetailProjection> result = conversationReader.search(condition);
        long totalCount = conversationReader.searchCount(condition);

        // Repository가 한 건 더 조회한 결과로 다음 페이지를 판단하고, 초과 항목은 응답에서 제외한다.
        boolean hasNext = result.size() > condition.limit();
        List<ConversationDetailProjection> page = hasNext ? result.subList(0, condition.limit()) : result;

        String cursor = null;
        UUID idAfter = null;
        if (hasNext){
            // 제외한 초과 항목이 아니라 마지막 반환 항목을 기준으로 해야 다음 페이지에서 누락되지 않는다.
            ConversationDetailProjection last = page.get(page.size() - 1);
            cursor = last.getConversation().getCreatedAt().toString();
            idAfter = last.getConversation().getId();
        }

        List<ConversationDto> dtos = page.stream()
                .map(projection -> conversationMapper.toConversationDto(condition.requesterId(), projection))
                .toList();

        return new CursorResponse<>(
                dtos,
                cursor,
                idAfter,
                hasNext,
                totalCount,
                condition.sortBy().getSortKey(),
                condition.sortDirection()
        );
    }

    @Override
    public void existsByIdAndParticipant(UUID id, UUID participant) {
        conversationReader.existsByIdAndParticipant(id, participant);
    }

    private ConversationDto createOrGetConversation(ConversationCreateCommand createCommand) {
        UUID requesterId = createCommand.requesterId();
        try {
            // 사전 조회 직후 다른 요청이 저장할 수 있으므로 DB UNIQUE가 최종 중복 방어를 맡는다.
            // Saver 프록시 호출이 정상 반환되면 커밋까지 완료되며, 그 이후 응답을 변환한다.
            return conversationMapper.toConversationDto(requesterId, conversationSaver.save(createCommand));
        } catch (DataIntegrityViolationException e) {
            if (!isConversationUniqueViolation(e)){
                throw new ConversationCreationFailedException(e);
            }
            // Saver의 실패 트랜잭션이 종료된 뒤 먼저 저장된 대화방을 조회한다.
            // 해당 UNIQUE 충돌이어도 조회 결과가 없으면 성공으로 간주하지 않는다.
            return conversationReader.findExistingConversation(createCommand)
                    .map(projection -> conversationMapper.toConversationDto(requesterId, projection))
                    .orElseThrow(() -> new ConversationCreationFailedException(e));
        }
    }

    /**
     * 다른 FK·NOT NULL·UNIQUE 오류를 중복 생성 성공으로 처리하지 않도록
     * PostgreSQL UNIQUE 오류 코드와 대화방 사용자 쌍 제약 이름을 함께 확인한다.
     */
    private boolean isConversationUniqueViolation(DataIntegrityViolationException exception){
        Throwable cause = exception;

        // Spring 예외 안에 Hibernate 예외가 여러 단계로 감싸질 수 있어 원인 체인을 탐색한다.
        while (cause != null){
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_VIOLATION_SQL_STATE.equals(violation.getSQLState())
                    && CONVERSATION_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())){
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }
}
