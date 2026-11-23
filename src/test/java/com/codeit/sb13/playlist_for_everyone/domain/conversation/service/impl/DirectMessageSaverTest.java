package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DM 저장 및 생성 이벤트 발행")
class DirectMessageSaverTest {

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private ConversationReader conversationReader;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DirectMessageSaver directMessageSaver;

    @ParameterizedTest(name = "발신자가 userA인가:{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("메시지를 저장한 뒤 실제 발신자와 수신자를 담은 생성 이벤트를 한 번 발행한다.")
    void save_publishesCreatedEventAfterSaving(boolean senderIsUserA){
        UUID userAId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID userBId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID conversationId = UUID.randomUUID();
        UUID directMessageId = UUID.randomUUID();

        User userA = createUser(userAId, "사용자A");
        User userB = createUser(userBId, "사용자B");

        Conversation conversation = new Conversation(new ConversationCommand(userA, userB));
        ReflectionTestUtils.setField(conversation, "id", conversationId);

        User sender = senderIsUserA ? userA : userB;
        User receiver = senderIsUserA ? userB : userA;

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, sender.getId(), "안녕하세요.");
        DirectMessage savedMessage = new DirectMessage(new DirectMessageCommand(conversation, sender, receiver, command.content()));
        ReflectionTestUtils.setField(savedMessage, "id", directMessageId);

        given(conversationReader.getForParticipant(conversationId, sender.getId()))
                .willReturn(conversation);
        given(directMessageRepository.save(any(DirectMessage.class)))
                .willReturn(savedMessage);

        DirectMessage result = directMessageSaver.save(command);
        assertThat(result).isSameAs(savedMessage);

        DirectMessageCreatedEvent expectedEvent = new DirectMessageCreatedEvent(directMessageId, conversationId, sender.getId(), sender.getName(), receiver.getId());
        InOrder order = inOrder(directMessageRepository, eventPublisher);
        ArgumentCaptor<DirectMessage> savedArgument = ArgumentCaptor.forClass(DirectMessage.class);
        order.verify(directMessageRepository).save(savedArgument.capture());
        order.verify(eventPublisher).publishEvent(expectedEvent);

        DirectMessage submitted = savedArgument.getValue();
        assertThat(submitted.getConversationId()).isEqualTo(conversationId);
        assertThat(submitted.getSenderId()).isEqualTo(sender.getId());
        assertThat(submitted.getReceiverId()).isEqualTo(receiver.getId());
        assertThat(submitted.getContent()).isEqualTo(command.content());

        verifyNoMoreInteractions(directMessageRepository, eventPublisher);
    }

    @Test
    @DisplayName("대화방 조회·참여 검증에 실패하면 원본 예외를 전달하고 저장과 이벤트 발행을 하지 않는다.")
    void save_doesNotSaveOrPublishWhenConversationLookupFails(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, "안녕하세요");
        ConversationNotFoundException exception = new ConversationNotFoundException(Map.of("conversationId", conversationId));

        given(conversationReader.getForParticipant(conversationId, requesterId))
                .willThrow(exception);
        assertThatThrownBy(() -> directMessageSaver.save(command))
                .isSameAs(exception);
        verify(conversationReader).getForParticipant(conversationId, requesterId);
        verifyNoInteractions(directMessageRepository, eventPublisher);
    }

    @Test
    @DisplayName("메시지 저장에 실패하면 원본 예외를 전달하고 생성 이벤트를 발행하지 않는다.")
    void save_doesNotPublishWhenRepositorySaveFails(){
        UUID conversationId = UUID.randomUUID();
        User sender = createUser(UUID.randomUUID(), "발신자");
        User receiver = createUser(UUID.randomUUID(), "수신자");

        Conversation conversation = new Conversation(new ConversationCommand(sender, receiver));
        ReflectionTestUtils.setField(conversation, "id", conversationId);

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, sender.getId(), "안녕하세요.");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("메시지 저장 실패");

        given(conversationReader.getForParticipant(conversationId, sender.getId()))
                .willReturn(conversation);
        given(directMessageRepository.save(any(DirectMessage.class)))
                .willThrow(exception);

        assertThatThrownBy(() -> directMessageSaver.save(command))
                .isSameAs(exception);

        verify(conversationReader).getForParticipant(conversationId, sender.getId());
        verify(directMessageRepository).save(any(DirectMessage.class));
        verifyNoInteractions(eventPublisher);

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
