package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageDeliveryServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DirectMessageDeliveryServiceImpl.class)
@DisplayName("DM 로컬 WebSocket 전달 서비스")
class DirectMessageDeliveryServiceTest {

    @Autowired
    private DirectMessageDeliveryService deliveryService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private final UUID conversationId = UUID.randomUUID();

    @Test
    @DisplayName("저장된 DM을 해당 대화방 경로로 한 번 전달한다.")
    void sendToLocalSubscribers_sendsMessageToConversationDestination(){
        MessageDto message = createMessageDto();

        deliveryService.sendToLocalSubscribers(message);

        verify(messagingTemplate).convertAndSend(
                "/sub/conversations/" + conversationId + "/direct-messages", message);

        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("전송에 실패한 DM은 재시도하지 않고 같은 대화방의 다음 DM을 전달한다.")
    void sendToLocalSubscribers_deliversNextMessageAfterFailureWithoutRetrying(){
        MessageDto failedMessage = createMessageDto(
                "전송에 실패할 메시지.",
                new UserSummary(UUID.randomUUID(), "발신자", null),
                new UserSummary(UUID.randomUUID(), "수신자", null),
                LocalDateTime.of(2026, 11, 16, 10, 0)
        );

        MessageDto nextMessage = createMessageDto(
                "다음 정상 메시지",
                failedMessage.sender(),
                failedMessage.receiver(),
                failedMessage.createdAt().plusSeconds(1)
        );

        String destination = "/sub/conversations/" + conversationId + "/direct-messages";

        doThrow(new MessageDeliveryException("테스트용 WebSocket 전송 실패"))
                .when(messagingTemplate)
                .convertAndSend(destination, failedMessage);

        assertThatCode(() -> deliveryService.sendToLocalSubscribers(failedMessage))
                .doesNotThrowAnyException();

        assertThatCode(() -> deliveryService.sendToLocalSubscribers(nextMessage))
                .doesNotThrowAnyException();

        verify(messagingTemplate).convertAndSend(destination,failedMessage);
        verify(messagingTemplate).convertAndSend(destination,nextMessage);
        verifyNoMoreInteractions(messagingTemplate);
    }

    private MessageDto createMessageDto() {
        return createMessageDto(
                "안녕하세요.",
                new UserSummary(UUID.randomUUID(), "발신자", null),
                new UserSummary(UUID.randomUUID(), "수신자", null),
                LocalDateTime.of(2026, 11, 16, 10, 0)
        );
    }

    private MessageDto createMessageDto(String content, UserSummary sender, UserSummary receiver, LocalDateTime createdAt) {
        return new MessageDto(
                UUID.randomUUID(),
                conversationId,
                createdAt,
                sender,
                receiver,
                content
        );
    }

}