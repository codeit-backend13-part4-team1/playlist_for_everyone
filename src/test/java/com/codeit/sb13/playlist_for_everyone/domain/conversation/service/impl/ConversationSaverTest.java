package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("대화방 저장")
class ConversationSaverTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private  ConversationUserReader conversationUserReader;

    private ConversationSaver conversationSaver;
    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        conversationSaver = new ConversationSaver(
                conversationRepository,
                conversationUserReader
        );
    }

    @ParameterizedTest(name = "요청자가 userA인가:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("요청 방향에 관계없이 사용자 쌍을 정렬하여 저장하고 저장소가 반환한 엔티티를 반환한다.")
    void save_returnsNewConversationRegardlessOfRequestDirection(boolean requesterIsUserA){
        UUID userAId = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID userBId = UUID.fromString("80000000-0000-0000-0000-000000000000");

        User userA = createUser(userAId, "사용자A");
        User userB = createUser(userBId, "사용자B");

        User requester = requesterIsUserA ? userA : userB;
        User otherUser = requesterIsUserA ? userB : userA;

        ConversationCreateCommand command = new ConversationCreateCommand(requester.getId(), otherUser.getId());
        UUID conversationId = UUID.randomUUID();
        Conversation saved = new Conversation(new ConversationCommand(userA, userB));
        ReflectionTestUtils.setField(saved, "id", conversationId);

        given(conversationUserReader.getById(requester.getId()))
                .willReturn(requester);
        given(conversationUserReader.getById(otherUser.getId()))
                .willReturn(otherUser);
        given(conversationRepository.save(any(Conversation.class)))
                .willReturn(saved);
        Conversation result = conversationSaver.save(command);

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());

        Conversation requested = captor.getValue();
        assertThat(requested.getUserA()).isSameAs(userA);
        assertThat(requested.getUserB()).isSameAs(userB);
        verify(conversationUserReader).getById(requester.getId());
        verify(conversationUserReader).getById(otherUser.getId());
    }

    @Test
    @DisplayName("요청자가 없으면 사용자 없음 예외를 그대로 전달하고 저장하지 않는다.")
    void save_throwsWhenRequesterNotFound(){


        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        UserNotFoundException exception = createUserNotFoundException(requesterId);
        given(conversationUserReader.getById(requesterId))
                .willThrow(exception);
        assertThatThrownBy(() -> conversationSaver.save(command))
                .isSameAs(exception);
        verify(conversationUserReader).getById(requesterId);
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("상대방이 없으면 대화 상대 미존재 예외로 변환하고 저장하지 않는다.")
    void save_throwsWhenTargetNotFound(){
        User requester = createUser(requesterId, "요청자");
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        given(conversationUserReader.getById(requesterId))
                .willReturn(requester);
        given(conversationUserReader.getById(withUserId))
                .willThrow(createUserNotFoundException(withUserId));
        assertThatThrownBy(() -> conversationSaver.save(command))
                .isInstanceOfSatisfying(ConversationTargetNotFoundException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsExactlyEntriesOf(Map.of("withUserId", withUserId)));
        verify(conversationUserReader).getById(requesterId);
        verify(conversationUserReader).getById(withUserId);
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("상대방 조회 중 다른 예외가 발생하면 원본 예외를 전달하고 저장하지 않는다.")
    void save_propagatesUnexpectedTargetLookupException(){
        User requester = createUser(requesterId, "요청자");
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("사용자 조회 중 DB 연결 실패");
        given(conversationUserReader.getById(requesterId))
                .willReturn(requester);
        given(conversationUserReader.getById(withUserId))
                .willThrow(exception);
        assertThatThrownBy(() -> conversationSaver.save(command))
                .isSameAs(exception);
        verify(conversationUserReader).getById(requesterId);
        verify(conversationUserReader).getById(withUserId);
        verifyNoInteractions(conversationRepository);
    }

    @Test
    @DisplayName("대화방 저장이 실패하면 원본 예외를 전달한다.")
    void save_propagatesRepositoryException(){
        User requester = createUser(requesterId, "요청자");
        User otherUser = createUser(withUserId, "대화 상대");

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        DataIntegrityViolationException exception = new DataIntegrityViolationException("대화방 저장 실패");
        given(conversationUserReader.getById(requesterId))
                .willReturn(requester);
        given(conversationUserReader.getById(withUserId))
                .willReturn(otherUser);
        given(conversationRepository.save(any(Conversation.class)))
                .willThrow(exception);

        assertThatThrownBy(() -> conversationSaver.save(command))
                .isSameAs(exception);

        verify(conversationUserReader).getById(requesterId);
        verify(conversationUserReader).getById(withUserId);
        verify(conversationRepository).save(any(Conversation.class));

    }

    private UserNotFoundException createUserNotFoundException(UUID userId) {
        return new UserNotFoundException(Map.of("userId", userId));
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


}
