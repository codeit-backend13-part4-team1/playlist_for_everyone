package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@DisplayName("SSE 로컬 전송 서비스")
class SseDeliveryServiceTest {

    @Test
    @DisplayName("수신자의 연결에 SSE 이벤트를 전송한다.")
    void sendEventToReceiverConnection() throws IOException {
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseDeliveryService service = new SseDeliveryService(repository);

        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = Mockito.mock(SseEmitter.class);
        repository.save(receiverId, Connection.create(), emitter);

        SseEvent<Map<String,String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        service.sendToLocalConnections(event);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
     }
}