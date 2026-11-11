package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("대화방 조회")
class ConversationReaderTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private UserSummaryMapper summaryMapper;

    private ConversationReader conversationReader;

    private final UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID withUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID conversationId = UUID.randomUUID();
    @BeforeEach
    void setUp() {
        conversationReader = new ConversationReader(conversationRepository, summaryMapper);
    }

    @Test
    @DisplayName("요청자가 userA이면 userB를 상대방으로 반환하고 메시지가 없으면 초기 상태를 반환한다.")
    void findExistingConversation_returnsOtherUserWhenRequesterIsUserA(){
        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");

        Conversation conversation = getConversation(requester, otherUser, conversationId);
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        given(projection.getConversation()).willReturn(conversation);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        UserSummary expectedWith = new UserSummary(withUserId, "대화 상대", null);
        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willReturn(Optional.of(projection));
        given(summaryMapper.toSummary(otherUser)).willReturn(expectedWith);

        Optional<ConversationDto> result = conversationReader.findExistingConversation(command);

        assertThat(result).hasValue(new ConversationDto(conversationId, expectedWith, null, false));
        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
        verify(summaryMapper).toSummary(otherUser);
    }

    @Test
    @DisplayName("요청자가 userB이면 userA를 대화상대로 반환한다.")
    void findExistingConversation_returnsOtherUserWhenRequesterIsUserB(){
        UUID withUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");

        Conversation conversation = getConversation(requester, otherUser, conversationId);
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        given(projection.getConversation()).willReturn(conversation);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        UserSummary expectedWith = new UserSummary(withUserId, "대화 상대", null);

        given(conversationRepository.findDetailByUserPair(withUserId, requesterId, requesterId))
                .willReturn(Optional.of(projection));

        given(summaryMapper.toSummary(otherUser))
                .willReturn(expectedWith);

        Optional<ConversationDto> result = conversationReader.findExistingConversation(command);
        assertThat(result).hasValueSatisfying(dto ->
                assertThat(dto.with()).isEqualTo(expectedWith));

        verify(conversationRepository).findDetailByUserPair(withUserId, requesterId, requesterId);
        verify(summaryMapper).toSummary(otherUser);
    }

    @Test
    @DisplayName("대화방이 없으면 빈 Optional을 반환하고 사용자 변환을 호출하지 않는다.")
    void findExistingConversation_returnsEmptyWhenConversationNotFound(){
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willReturn(Optional.empty());

        Optional<ConversationDto> result = conversationReader.findExistingConversation(command);
        assertThat(result).isEmpty();
        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
        verifyNoInteractions(summaryMapper);
    }

    @ParameterizedTest(name = "[{index}] 요청자={0}, 상대방={1}")
    @CsvSource({
            "7fffffff-ffff-ffff-ffff-ffffffffffff, 80000000-0000-0000-0000-000000000000",
            "80000000-0000-0000-0000-000000000000, 7fffffff-ffff-ffff-ffff-ffffffffffff"
    })
    @DisplayName("요청 방향과 관계없이 UUID 문자열순서로 정렬하여 대화방을 조회한다.")
    void findExistingConversation_sortsUserIdsByStringOrder(
            String requesterIdValue,
            String otherUserIdValue
    ){
        UUID expectedUserAId = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID expectedUserBId = UUID.fromString("80000000-0000-0000-0000-000000000000");

        ConversationCreateCommand command = new ConversationCreateCommand(UUID.fromString(requesterIdValue), UUID.fromString(otherUserIdValue));

        given(conversationRepository.findDetailByUserPair(expectedUserAId, expectedUserBId, command.requesterId()))
                .willReturn(Optional.empty());

        conversationReader.findExistingConversation(command);
        verify(conversationRepository).findDetailByUserPair(expectedUserAId, expectedUserBId, command.requesterId());
    }

    @Test
    @DisplayName("대화방 조회가 실패하면 원본 예외를 전달하고 사용자 변환을 호출하지 않는다.")
    void findExistingConversation_propagatesRepositoryFailure(){
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("대화방 조회 중 DB 연결 실패");

        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willThrow(exception);

        assertThatThrownBy(()-> conversationReader.findExistingConversation(command))
                .isSameAs(exception);
        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
        verifyNoInteractions(summaryMapper);
    }

    @Test
    @DisplayName("기존 대화방의 최신 메시지를 응답에 반영한다.")
    void findExistingConversation_returnsLastestMessage(){
        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 11, 10, 0);

        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");

        Conversation conversation = getConversation(requester, otherUser, conversationId);

        DirectMessage lastMessage = createMessage(messageId, conversation, requester, otherUser, createdAt, "안녕하세요.");
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        given(projection.getConversation()).willReturn(conversation);
        given(projection.getLastMessage()).willReturn(lastMessage);

        UserSummary senderSummery = new UserSummary(requesterId, "요청자", null);
        UserSummary receiverSummery = new UserSummary(withUserId, "대화 상대", null);

        given(summaryMapper.toSummary(requester)).willReturn(senderSummery);
        given(summaryMapper.toSummary(otherUser)).willReturn(receiverSummery);
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willReturn(Optional.of(projection));

        MessageDto expectedMessage = new MessageDto(messageId, conversationId, createdAt, senderSummery, receiverSummery, "안녕하세요.");

        Optional<ConversationDto> result = conversationReader.findExistingConversation(command);
        assertThat(result).hasValueSatisfying(dto ->
                assertThat(dto.lastestMessage()).isEqualTo(expectedMessage));
        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
    }

    private User createUser(UUID userId, String name) {
        User user = User.builder()
                .email(userId + "@example.com")
                .name(name)
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id",
                userId);
        return user;
    }

    @ParameterizedTest(name = "hasUnread={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("요청자가 userB여도 조회된 미읽음 여부를 응답에 그대로 반영한다.")
    void findExistingConversation_preservesUnreadStatusForRequester(boolean hasUnread){
        UUID withUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");
        Conversation conversation = getConversation(requester, otherUser, conversationId);

        DirectMessage lastMessage = createMessage(UUID.randomUUID(), conversation, requester, otherUser, LocalDateTime.of(2026, 11, 11, 10, 0), "가장 최근에 보낸 메시지");

        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        given(projection.getConversation()).willReturn(conversation);
        given(projection.getLastMessage()).willReturn(lastMessage);
        given(projection.getHasUnread()).willReturn(hasUnread);
        given(summaryMapper.toSummary(requester))
                .willReturn(new UserSummary(requesterId, "요청자", null));
        given(summaryMapper.toSummary(otherUser))
                .willReturn(new UserSummary(withUserId, "대화 상대", null));
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        given(conversationRepository.findDetailByUserPair(withUserId, requesterId, requesterId))
                .willReturn(Optional.of(projection));

        Optional<ConversationDto> result = conversationReader.findExistingConversation(command);
        assertThat(result).hasValueSatisfying(dto ->
                assertThat(dto.hasUnread()).isEqualTo(hasUnread));
        verify(conversationRepository).findDetailByUserPair(withUserId, requesterId, requesterId);
    }


    private Conversation getConversation(User requester, User otherUser, UUID conversationId) {
        Conversation conversation = new Conversation(new ConversationCommand(requester, otherUser));
        ReflectionTestUtils.setField(conversation, "id", conversationId);
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
}