package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageReader;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageSaver;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageUpdater;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DirectMessageServiceImpl.class)
@DisplayName("DM 메시지 송신 서비스")
public class DirectMessageSendServiceTest {

    @Autowired
    private DirectMessageService directMessageService;

    @MockitoBean
    private DirectMessageSaver directMessageSaver;

    @MockitoBean
    private DirectMessageMapper directMessageMapper;

    @MockitoBean
    DirectMessagePublisher directMessagePublisher;

    @MockitoBean
    private DirectMessageReader directMessageReader;

    @MockitoBean
    private DirectMessageUpdater directMessageUpdater;

    @MockitoBean
    private  ConversationService conversationService;

    @Test
    @DisplayName("저장된 메시지를 응답으로 변환하여 한 번 발행하고 반환한다.")
    void send_publishesAndReturnsSavedMessage(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, "안녕하세요.");

        DirectMessage savedMessage = mock(DirectMessage.class);

        MessageDto expected = new MessageDto(
                UUID.randomUUID(),
                conversationId,
                LocalDateTime.of(2026, 11, 15, 10, 0),
                new UserSummary(requesterId, "발신자", null),
                new UserSummary(receiverId, "수신자", null),
                command.content()
        );

        given(directMessageSaver.save(command)).willReturn(savedMessage);
        given(directMessageMapper.toMessageDto(savedMessage)).willReturn(expected);

        MessageDto result = directMessageService.send(command);
        assertThat(result).isEqualTo(expected);

        InOrder order = inOrder(directMessageSaver, directMessageMapper, directMessagePublisher);

        order.verify(directMessageSaver).save(command);
        order.verify(directMessageMapper).toMessageDto(savedMessage);
        order.verify(directMessagePublisher).publish(expected);

        verifyNoMoreInteractions(directMessageSaver, directMessageMapper, directMessagePublisher);
    }

    @Test
    @DisplayName("저장에 실패하면 원본 예외를 전달하고 응답 변환과 발행을 수행하지 않는다.")
    void send_doesNotMapOrPublishWhenSaveFails(){
        DirectMessageSendCommand command = new DirectMessageSendCommand(UUID.randomUUID(), UUID.randomUUID(), "안녕하세요.");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("DM 저장 실패");

        given(directMessageSaver.save(command)).willThrow(exception);
        assertThatThrownBy(() -> directMessageService.send(command))
                .isSameAs(exception);

        verify(directMessageSaver).save(command);
        verifyNoInteractions( directMessageMapper, directMessagePublisher);
    }

    @Test
    @DisplayName("저장 후 응답 변환에 실패하면 저장 정보를 담은 발행 예외를 전달하고 발행하지 않는다.")
    void send_reportsPersistedMessageWhenMappingFails(){
        UUID conversationId = UUID.randomUUID();
        UUID directMessageId = UUID.randomUUID();

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, UUID.randomUUID(), "안녕하세요.");
        DirectMessage savedMessage = mock(DirectMessage.class);
        IllegalStateException cause = new IllegalStateException("프로필 이미지 URL 생성 실패");
        given(directMessageSaver.save(command)).willReturn(savedMessage);
        given(savedMessage.getId()).willReturn(directMessageId);

        given(directMessageMapper.toMessageDto(savedMessage)).willThrow(cause);

        assertThatThrownBy(() -> directMessageService.send(command))
                .isInstanceOfSatisfying(DirectMessagePublishException.class,
                        e -> {
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getApiErrorCode())
                            .isEqualTo(ApiErrorCode.DIRECT_MESSAGE_PUBLISH_FAILED);
                    assertThat(e.getDetails()).containsEntry("directMessageId", directMessageId)
                            .containsEntry("conversationId", conversationId)
                            .containsEntry("persisted", true);
                });

        verify(directMessageSaver).save(command);
        verify(directMessageMapper).toMessageDto(savedMessage);
        verifyNoInteractions(directMessagePublisher);

    }

    @Test
    @DisplayName("발행에 실패하면 저장 정보를 담은 예외를 그대로 전달하고 재저장하거나 재발행하지 않는다.")
    void send_propagatesPublishFailureWithoutRetrying(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID directMessageId = UUID.randomUUID();

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, "안녕하세요.");
        DirectMessage savedMessage = mock(DirectMessage.class);
        MessageDto messageDto = new MessageDto(
                directMessageId,
                conversationId,
                LocalDateTime.of(2026, 11, 15, 10, 0),
                new UserSummary(requesterId, "발신자", null),
                new UserSummary(receiverId, "수신자", null),
                command.content()
        );

        DirectMessagePublishException exception = new DirectMessagePublishException(directMessageId, conversationId, new IllegalStateException("메시지 브로커 발행 실패"));
        given(directMessageSaver.save(command)).willReturn(savedMessage);
        given(directMessageMapper.toMessageDto(savedMessage)).willReturn(messageDto);
        doThrow(exception)
                .when(directMessagePublisher)
                .publish(messageDto);

        assertThatThrownBy(() -> directMessageService.send(command))
                .isSameAs(exception);
        verify(directMessageSaver).save(command);
        verify(directMessageMapper).toMessageDto(savedMessage);
        verify(directMessagePublisher).publish(messageDto);

        verifyNoMoreInteractions(
                directMessageSaver,
                directMessageMapper,
                directMessagePublisher
        );
    }




}
