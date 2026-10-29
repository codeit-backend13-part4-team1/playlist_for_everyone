package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.Lifecycle;
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

    @Test
    @DisplayName("Lifecycle 빈이 종료되기 전에 SSE 에미터 종료와 연결 삭제를 요청한다.")
    void contextCloseCleansConnectionsBeforeLifecycleStop(){
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();

        SseEmitter emitter = mock(SseEmitter.class);
        Lifecycle lifecycle = mock(Lifecycle.class);

        when(repository.findAll()).thenReturn(
                Map.of(userId,
                        Map.of(connection, emitter)
                )
        );

        when(lifecycle.isRunning()).thenReturn(true);

        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()){
            context.registerBean(SseConnectionService.class,
                    ()-> new SseConnectionService(repository, properties));
            context.registerBean("testLifecycle", Lifecycle.class, () -> lifecycle);
            context.refresh();

        }

        InOrder completionOrder = inOrder(emitter, lifecycle);
        completionOrder.verify(emitter).complete();
        completionOrder.verify(lifecycle).stop();

        InOrder deletionOrder = inOrder(repository, lifecycle);
        deletionOrder.verify(repository).delete(userId, connection);
        deletionOrder.verify(lifecycle).stop();
    }
}
