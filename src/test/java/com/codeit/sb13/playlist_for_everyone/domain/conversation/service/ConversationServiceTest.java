package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationReader;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationSaver;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("대화방 생성 서비스")
class ConversationServiceTest {

    @Mock
    private ConversationReader conversationReader;

    @Mock
    private ConversationSaver conversationSaver;

    private ConversationService conversationService;
    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(conversationReader, conversationSaver);
    }

    @Test
    @DisplayName("기존 대화방이 없으면 저장 컴포넌트에 명령을 전달하고 생성 결과를 반환한다.")
    void create_createsNewConversation(){
        ConversationCreateCommand command = getCreateCommand();
        ConversationDto savedConversation = getConversation("대화상대");

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty());
        given(conversationSaver.save(command))
                .willReturn(savedConversation);

        ConversationDto result = conversationService.create(command);
        assertThat(result).isEqualTo(savedConversation);

        verify(conversationReader).findExistingConversation(command);
        verify(conversationSaver).save(command);
    }

    @Test
    @DisplayName("기존 대화방이 있으면 해당 대화방을 반환하고 새로 저장하지 않는다.")
    void create_returnsExistingConversation(){
        ConversationCreateCommand command = getCreateCommand();
        ConversationDto existingConversation = getConversation("대화 상대");

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.of(existingConversation));

        ConversationDto result = conversationService.create(command);

        assertThat(result).isEqualTo(existingConversation);
        verify(conversationReader).findExistingConversation(command);
        verifyNoInteractions(conversationSaver);
    }

    @Test
    @DisplayName("자기 자신과의 대화방 생성을 요청하면 예외가 발생하고 조회와 저장을 수행하지 않는다.")
    void create_rejectsSelfConversation(){
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, requesterId);

        assertThatThrownBy(() -> conversationService.create(command))
                .isInstanceOf(SelfConversationNotAllowedException.class);
        verifyNoInteractions(conversationReader, conversationSaver);
    }

    @Test
    @DisplayName("대화방 사용자 쌍 UNIQUE 충돌이 발생하면 재조회하여 기존 대화방을 반환한다.")
    void create_returnsExistingConversationAfterUniqueViolation(){
        ConversationCreateCommand command = getCreateCommand();
        ConversationDto existingConversation = getConversation("대화 상대");

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existingConversation));

        DataIntegrityViolationException exception = createDuplicateConversationException();
        given(conversationSaver.save(command)).willThrow(exception);

        ConversationDto result = conversationService.create(command);

        assertThat(result).isEqualTo(existingConversation);
        verify(conversationReader, times(2)).findExistingConversation(command);
        verify(conversationSaver).save(command);

    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonDuplicateIntegrityViolations")
    @DisplayName("대화방 중복 이외의 무결성 오류는 재조회 없이 생성 실패로 처리한다.")
    void create_throwsWhenIntegrityViolationIsNotConversationDuplicate(
            String scenario,
            DataIntegrityViolationException exception
    ){
        ConversationCreateCommand command = getCreateCommand();
        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty());
        given(conversationSaver.save(command))
                .willThrow(exception);

        assertThatThrownBy(() -> conversationService.create(command))
                .isInstanceOfSatisfying(ConversationCreationFailedException.class,
                        actual -> assertThat(actual.getCause())
                                .isSameAs(exception));

        verify(conversationReader).findExistingConversation(command);
        verify(conversationSaver).save(command);
    }

    @Test
    @DisplayName("대화방 UNIQUE 충돌 후 기존 대화방을 찾지 못하면 생성 실패 예외가 발생한다.")
    void create_throwsWhenExistingConversationMissingAfterUniqueViolation(){
        ConversationCreateCommand command = getCreateCommand();
        DataIntegrityViolationException exception = createDuplicateConversationException();

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty());
        given(conversationSaver.save(command))
                .willThrow(exception);

        assertThatThrownBy(() -> conversationService.create(command))
                .isInstanceOfSatisfying(ConversationCreationFailedException.class,
                        actual -> assertThat(actual.getCause())
                                .isSameAs(exception));
        verify(conversationReader,times(2)).findExistingConversation(command);
        verify(conversationSaver).save(command);
    }

    @Test
    @DisplayName("최초 대화방 조회가 실패하면 원본 예외를 전달하고 저장하지 않는다.")
    void create_propagatesInitialLookupFailure(){
        ConversationCreateCommand command = getCreateCommand();
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("대화방 조회중 DB 연결 실패");

        given(conversationReader.findExistingConversation(command))
                .willThrow(exception);
        assertThatThrownBy(() -> conversationService.create(command))
                .isSameAs(exception);

        verify(conversationReader).findExistingConversation(command);
        verifyNoInteractions(conversationSaver);
    }

    @Test
    @DisplayName("대화 상대 미존재 예외가 발생하면 그대로 전달하고 재조회하지 않는다.")
    void  create_propagatesTargetNotFoundException(){
        ConversationCreateCommand command = getCreateCommand();
        ConversationTargetNotFoundException exception = new ConversationTargetNotFoundException(Map.of("withUserId", withUserId));

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty());
        given(conversationSaver.save(command))
                .willThrow(exception);

        assertThatThrownBy(() -> conversationService.create(command))
                .isSameAs(exception);
        verify(conversationReader).findExistingConversation(command);
        verify(conversationSaver).save(command);
    }

    private static Stream<Arguments> nonDuplicateIntegrityViolations(){
        return Stream.of(
                Arguments.of("제약 조건 이름이 같아도 외래키 위반이면 실패한다.",
                        createIntegrityViolationException("23503", "uq_conversations_user_pair")),
                Arguments.of("NOT NULL 위반이면 실패한다.",
                        createIntegrityViolationException("23502", null)),
                Arguments.of("다른 UNIQUE 제약 조건 위반 이면 실패한다.",
                        createIntegrityViolationException("23505", "other_unique_constraint")),
                Arguments.of("원인 정보가 없는 무결성 오류이면 실패한다.",
                        new DataIntegrityViolationException("원인 정보 없는 무결성 오류"))
        );
    }

    private static DataIntegrityViolationException createIntegrityViolationException(
            String sqlState,
            String constraintName
    ){
        SQLException sqlException = new SQLException("DB 제약 조건 위반", sqlState);
        ConstraintViolationException constraintException = new ConstraintViolationException("DB 제약 조건 위반", sqlException, constraintName);
        return new DataIntegrityViolationException("대화방 저장 실패", constraintException);
    }

    private DataIntegrityViolationException createDuplicateConversationException() {
        SQLException sqlException = new SQLException("대화방 사용자 쌍 중복", "23505");
        ConstraintViolationException constraintException = new ConstraintViolationException("대화방 사용자 쌍 중복", sqlException, "uq_conversations_user_pair");
        return new DataIntegrityViolationException("대화방 중복 저장", constraintException);
    }

    private ConversationDto getConversation(String name) {
        return new ConversationDto(UUID.randomUUID(), new UserSummary(withUserId, name, null), null, false);
    }

    private ConversationCreateCommand getCreateCommand() {
        return new ConversationCreateCommand(requesterId, withUserId);
    }


}
