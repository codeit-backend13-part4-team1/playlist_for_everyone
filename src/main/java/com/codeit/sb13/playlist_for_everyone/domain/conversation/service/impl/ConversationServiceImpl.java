package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.ConversationMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 자기 대화 검증, 기존 대화방 조회, 신규 저장, 응답 변환 순서를 조정한다.
 *
 * <p>이 서비스는 직접 트랜잭션을 시작하지 않는다. 조회는 Reader에, 독립적인 저장 트랜잭션은
 * Saver에 맡겨 저장 실패가 조회·응답 변환 단계까지 같은 트랜잭션으로 묶이지 않도록 한다.
 *
 * <p>상위 트랜잭션이 없는 컨트롤러 호출에서는 조회 또는 저장 트랜잭션이 끝난 뒤 DTO를 변환한다.
 * 상위 트랜잭션이 있으면 조회와 변환은 그 영향을 받지만, Saver의 저장 커밋은 독립적이다.
 * 저장 커밋 이후 프로필 이미지 URL 생성에 실패해도 이미 생성된 대화방은 유지된다.
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

    @Override
    public ConversationDto findWithUser(ConversationFindCondition condition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConversationDto findById(ConversationFindByIdCondition condition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CursorResponse<ConversationDto> search(ConversationSearchCondition condition) {
        throw new UnsupportedOperationException();
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
