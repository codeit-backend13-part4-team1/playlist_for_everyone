package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.ConversationMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

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

    private ConversationDto createOrGetConversation(ConversationCreateCommand createCommand) {
        UUID requesterId = createCommand.requesterId();
        try {
            return conversationMapper.toConversationDto(requesterId, conversationSaver.save(createCommand));
        } catch (DataIntegrityViolationException e) {
            if (!isConversationUniqueViolation(e)){
                throw new ConversationCreationFailedException(e);
            }
            return conversationReader.findExistingConversation(createCommand)
                    .map(projection -> conversationMapper.toConversationDto(requesterId, projection))
                    .orElseThrow(() -> new ConversationCreationFailedException(e));
        }
    }

    private boolean isConversationUniqueViolation(DataIntegrityViolationException exception){
        Throwable cause = exception;

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
