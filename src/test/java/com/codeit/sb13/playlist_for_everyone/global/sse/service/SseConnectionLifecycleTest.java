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

/**
 * 실제 Spring 컨텍스트를 닫아 ContextClosedEvent와 연결 정리 메서드의 연결을 확인한다.
 * 서비스 메서드를 직접 호출하는 단위 테스트와 달리 EventListener 등록까지 검증한다.
 * emitter와 저장소는 Mock이므로 실제 HTTP 응답 종료까지 완료됐다는 뜻은 아니다.
 */
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

        // try 블록을 벗어날 때 context.close가 호출되며 실제 종료 이벤트가 발행된다.
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

        // 일반 Lifecycle.stop보다 연결 정리 요청이 먼저라는 순서를 검증한다.
        // 스케줄러도 ContextClosedEvent를 받을 수 있으므로 모든 하트비트가 이미 멈췄다고 보장하지는 않는다.
        InOrder completionOrder = inOrder(emitter, lifecycle);
        completionOrder.verify(emitter).complete();
        completionOrder.verify(lifecycle).stop();

        InOrder deletionOrder = inOrder(repository, lifecycle);
        deletionOrder.verify(repository).delete(userId, connection);
        deletionOrder.verify(lifecycle).stop();
    }
}
