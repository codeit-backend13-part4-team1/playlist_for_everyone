package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.ConversationMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationReader;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationSaver;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDateTime;
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

    @Mock
    private UserSummaryMapper userSummaryMapper;

    private ConversationService conversationService;
    private final UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID withUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        DirectMessageMapper messageMapper = new DirectMessageMapper(userSummaryMapper);
        ConversationMapper conversationMapper = new ConversationMapper(userSummaryMapper, messageMapper);
        conversationService = new ConversationServiceImpl(conversationReader, conversationSaver, conversationMapper);
    }

    @ParameterizedTest(name = "요청자가 userA인가:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("신규 대화방은 요청 방향에 맞는 상대방과 메시지 없는 초기 상태를 반환한다.")
    void create_createsNewConversationRegardlessOfRequestDirection(boolean requesterIsUserA){
        User userA = createUser(requesterId, "사용자A");
        User userB = createUser(withUserId, "사용자B");
        User requester = requesterIsUserA ? userA : userB;
        User otherUser = requesterIsUserA ? userB : userA;
        ConversationCreateCommand command = new ConversationCreateCommand(requester.getId(), otherUser.getId());
        Conversation savedConversation = createConversation(userA, userB);
        UserSummary expectedWith = new UserSummary(otherUser.getId(), otherUser.getName(), "https://example.com/profile.png");

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty());
        given(conversationSaver.save(command))
                .willReturn(savedConversation);
        given(userSummaryMapper.toSummary(otherUser)).willReturn(expectedWith);

        ConversationDto result = conversationService.create(command);
        assertThat(result).isEqualTo(new ConversationDto(savedConversation.getId(), expectedWith, null, false));

        verify(conversationReader).findExistingConversation(command);
        verify(conversationSaver).save(command);
        verify(userSummaryMapper).toSummary(otherUser);
    }

    @ParameterizedTest(name = "요청자가 userA인가:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("기존 대화방은 요청 방향에 맞는 상대방을 반환하고 새로 저장하지 않는다.")
    void create_returnsExistingConversationRegardlessOfRequestDirection(boolean requesterIsUserA){
        User userA = createUser(requesterId, "사용자A");
        User userB = createUser(withUserId, "사용자B");
        User requester = requesterIsUserA ? userA : userB;
        User otherUser = requesterIsUserA ? userB : userA;
        ConversationCreateCommand command = new ConversationCreateCommand(requester.getId(), otherUser.getId());
        Conversation existingConversation = createConversation(userA, userB);
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        UserSummary expectedWith = new UserSummary(otherUser.getId(), otherUser.getName(), null);

        given(projection.getConversation()).willReturn(existingConversation);
        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.of(projection));
        given(userSummaryMapper.toSummary(otherUser)).willReturn(expectedWith);

        ConversationDto result = conversationService.create(command);

        assertThat(result).isEqualTo(new ConversationDto(existingConversation.getId(), expectedWith, null, false));
        verify(conversationReader).findExistingConversation(command);
        verifyNoInteractions(conversationSaver);
        verify(userSummaryMapper).toSummary(otherUser);
    }

    @Test
    @DisplayName("기존 대화방의 최신 메시지를 발신자와 수신자 요약 정보와 함께 응답으로 변환한다.")
    void create_returnsExistingConversationWithLatestMessage(){
        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");
        Conversation conversation = createConversation(requester, otherUser);
        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 11, 10, 0);
        DirectMessage lastMessage = createMessage(messageId, conversation, requester, otherUser, createdAt, "안녕하세요.");
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        UserSummary senderSummary = new UserSummary(requesterId, "요청자", null);
        UserSummary receiverSummary = new UserSummary(withUserId, "대화 상대", "https://example.com/profile.png");
        ConversationCreateCommand command = getCreateCommand();

        given(projection.getConversation()).willReturn(conversation);
        given(projection.getLastMessage()).willReturn(lastMessage);
        given(conversationReader.findExistingConversation(command)).willReturn(Optional.of(projection));
        given(userSummaryMapper.toSummary(requester)).willReturn(senderSummary);
        given(userSummaryMapper.toSummary(otherUser)).willReturn(receiverSummary);

        ConversationDto result = conversationService.create(command);

        MessageDto expectedMessage = new MessageDto(messageId, conversation.getId(), createdAt,
                senderSummary, receiverSummary, "안녕하세요.");
        assertThat(result).isEqualTo(new ConversationDto(conversation.getId(), receiverSummary, expectedMessage, false));
        verify(conversationReader).findExistingConversation(command);
        verifyNoInteractions(conversationSaver);
    }

    @ParameterizedTest(name = "hasUnread={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("요청자가 userB여도 조회된 미읽음 여부를 응답에 그대로 반영한다.")
    void create_preservesUnreadStatusForRequester(boolean hasUnread){
        User userA = createUser(requesterId, "사용자A");
        User userB = createUser(withUserId, "사용자B");
        Conversation conversation = createConversation(userA, userB);
        ConversationCreateCommand command = new ConversationCreateCommand(userB.getId(), userA.getId());
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        UserSummary expectedWith = new UserSummary(userA.getId(), userA.getName(), null);

        given(projection.getConversation()).willReturn(conversation);
        given(projection.getHasUnread()).willReturn(hasUnread);
        given(conversationReader.findExistingConversation(command)).willReturn(Optional.of(projection));
        given(userSummaryMapper.toSummary(userA)).willReturn(expectedWith);

        ConversationDto result = conversationService.create(command);

        assertThat(result).isEqualTo(new ConversationDto(conversation.getId(), expectedWith, null, hasUnread));
        verify(conversationReader).findExistingConversation(command);
        verifyNoInteractions(conversationSaver);
    }

    @Test
    @DisplayName("자기 자신과의 대화방 생성을 요청하면 예외가 발생하고 조회와 저장을 수행하지 않는다.")
    void create_rejectsSelfConversation(){
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, requesterId);

        assertThatThrownBy(() -> conversationService.create(command))
                .isInstanceOf(SelfConversationNotAllowedException.class);
        verifyNoInteractions(conversationReader, conversationSaver, userSummaryMapper);
    }

    @Test
    @DisplayName("대화방 사용자 쌍 UNIQUE 충돌이 발생하면 재조회하여 기존 대화방을 반환한다.")
    void create_returnsExistingConversationAfterUniqueViolation(){
        ConversationCreateCommand command = getCreateCommand();
        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");
        Conversation existingConversation = createConversation(requester, otherUser);
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        UserSummary expectedWith = new UserSummary(withUserId, "대화 상대", null);

        given(projection.getConversation()).willReturn(existingConversation);
        given(userSummaryMapper.toSummary(otherUser)).willReturn(expectedWith);

        given(conversationReader.findExistingConversation(command))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(projection));

        DataIntegrityViolationException exception = createDuplicateConversationException();
        given(conversationSaver.save(command)).willThrow(exception);

        ConversationDto result = conversationService.create(command);

        assertThat(result).isEqualTo(new ConversationDto(existingConversation.getId(), expectedWith, null, false));
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

    private User createUser(UUID userId, String name) {
        User user = User.builder()
                .email(userId + "@example.com")
                .name(name)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Conversation createConversation(User firstUser, User secondUser) {
        Conversation conversation = new Conversation(new ConversationCommand(firstUser, secondUser));
        ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
        return conversation;
    }

    private DirectMessage createMessage(UUID messageId, Conversation conversation,
                                        User sender, User receiver, LocalDateTime createdAt, String content) {
        DirectMessage message = BeanUtils.instantiateClass(DirectMessage.class);
        ReflectionTestUtils.setField(message, "id", messageId);
        ReflectionTestUtils.setField(message, "conversation", conversation);
        ReflectionTestUtils.setField(message, "sender", sender);
        ReflectionTestUtils.setField(message, "receiver", receiver);
        ReflectionTestUtils.setField(message, "content", content);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private ConversationCreateCommand getCreateCommand() {
        return new ConversationCreateCommand(requesterId, withUserId);
    }


}
