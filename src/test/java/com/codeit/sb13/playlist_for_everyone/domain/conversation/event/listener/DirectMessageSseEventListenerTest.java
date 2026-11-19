package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageReader;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DM 저장 후 SSE 전송 리스너")
class DirectMessageSseEventListenerTest {

    @Mock
    private DirectMessageReader directMessageReader;

    @Mock
    private DirectMessageMapper directMessageMapper;

    @Mock
    private SseEventPublisher sseEventPublisher;

    @InjectMocks
    private DirectMessageSseEventListener listener;

    @Captor
    private ArgumentCaptor<SseEvent<?>> eventCaptor;
    private final UUID directMessageId = UUID.randomUUID();

    @Test
    @DisplayName("메시지를 조회·변환하여 수신자에게 direct-messages SSE 이벤트를 한 번 발행한다.")
    void handleDirectMessageCreated_publishesDirectMessageEventOnce(){
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(directMessageId, conversationId, senderId, "발신자", receiverId);
        DirectMessage message = mock(DirectMessage.class);
        MessageDto messageDto = new MessageDto(
                directMessageId,
                conversationId,
                LocalDateTime.of(2026, 11, 18, 10, 0),
                new UserSummary(senderId, "발신자", null),
                new UserSummary(receiverId, "수신자", null),
                "테스트 메시지"
        );

        given(directMessageReader.getById(directMessageId)).willReturn(message);
        given(directMessageMapper.toMessageDto(message)).willReturn(messageDto);
        listener.handleDirectMessageCreated(event);

        InOrder order = inOrder(directMessageReader, directMessageMapper, sseEventPublisher);
        order.verify(directMessageReader).getById(directMessageId);
        order.verify(directMessageMapper).toMessageDto(message);
        order.verify(sseEventPublisher).publish(eventCaptor.capture());

        SseEvent<?> publishedEvent = eventCaptor.getValue();

        assertThat(publishedEvent.eventId()).isEqualTo(directMessageId);
        assertThat(publishedEvent.receiverId()).isEqualTo(receiverId);
        assertThat(publishedEvent.eventName()).isEqualTo("direct-messages");
        assertThat(publishedEvent.data()).isEqualTo(messageDto);
        verifyNoMoreInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("메시지 조회에 실패하면 예외를 전파하지 않고 DTO 변환과 SSE 발행을 중단한다.")
    void handleDirectMessageCreated_stopsWhenMessageLookupFails(){
        DirectMessageCreatedEvent event = getEvent();

        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("메시지 조회 실패");
        given(directMessageReader.getById(directMessageId)).willThrow(exception);
        assertThatCode(() -> listener.handleDirectMessageCreated(event))
                .doesNotThrowAnyException();
        verify(directMessageReader).getById(directMessageId);
        verifyNoMoreInteractions(directMessageMapper, sseEventPublisher);
    }

    @Test
    @DisplayName("DTO 변환에 실패하면 예외를 전파하지 않고 SSE 발행을 중단한다.")
    void handleDirectMessageCreated_stopsWhenMessageMappingFails(){

        DirectMessageCreatedEvent event = getEvent();

        DirectMessage message = mock(DirectMessage.class);
        IllegalStateException exception = new IllegalStateException("프로필 이미지 URL 생성 실패");
        given(directMessageReader.getById(directMessageId)).willReturn(message);
        given(directMessageMapper.toMessageDto(message)).willThrow(exception);
        assertThatCode(() -> listener.handleDirectMessageCreated(event))
                .doesNotThrowAnyException();

        verify(directMessageReader).getById(directMessageId);
        verify(directMessageMapper).toMessageDto(message);
        verifyNoMoreInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("SSE 발행에 실패하면 예외를 전파하지 않고 추가 발행을 시도하지 않는다.")
    void handleDirectMessageCreated_doesNotPropagatePublishFailure(){
        DirectMessageCreatedEvent event = getEvent(UUID.randomUUID());

        DirectMessage message = mock(DirectMessage.class);
        MessageDto messageDto = new MessageDto(
                event.directMessageId(),
                event.conversationId(),
                LocalDateTime.of(2026, 11, 18, 10, 0),
                new UserSummary(event.senderId(), event.senderName(), null),
                new UserSummary(event.receiverId(), "수신자", null),
                "테스트 메시지"
        );

        SsePublishException exception = new SsePublishException(new IllegalStateException("SSE 발행 실패"));
        given(directMessageReader.getById(event.directMessageId())).willReturn(message);
        given(directMessageMapper.toMessageDto(message)).willReturn(messageDto);
        doThrow(exception)
                .when(sseEventPublisher).publish(any());

        assertThatCode(() -> listener.handleDirectMessageCreated(event))
                .doesNotThrowAnyException();
        verify(directMessageReader).getById(event.directMessageId());
        verify(directMessageMapper).toMessageDto(message);
        verify(sseEventPublisher).publish(any());
        verifyNoMoreInteractions(sseEventPublisher);
    }

    private DirectMessageCreatedEvent getEvent(UUID directMessageId) {
        return new DirectMessageCreatedEvent(
                directMessageId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "발신자",
                UUID.randomUUID()
        );
    }
    private DirectMessageCreatedEvent getEvent() {
        return getEvent(directMessageId);
    }


}
