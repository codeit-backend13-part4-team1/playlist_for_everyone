package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 연결 종료 생명주기")
public class SseConnectionLifecycleTest {

    @Mock
    SseEmitterRepository repository;

    @Test
    @DisplayName("컨텍스트가 종료되면 등록된 에미터의 종료와 연결 삭제를 요청한다.")
    void contextCloseRequestsConnectionCleanup(){
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();
        SseEmitter emitter = mock(SseEmitter.class);

        when(repository.findAll()).thenReturn(
                Map.of(userId,
                        Map.of(connection, emitter)
                )
        );

        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()){
            context.registerBean(SseConnectionService.class,
                    ()-> new SseConnectionService(repository, properties));

            context.refresh();
            verify(emitter, never()).complete();
            verify(repository, never()).delete(any(), any());
        }

        verify(emitter).complete();
        verify(repository).delete(userId, connection);

    }
}
