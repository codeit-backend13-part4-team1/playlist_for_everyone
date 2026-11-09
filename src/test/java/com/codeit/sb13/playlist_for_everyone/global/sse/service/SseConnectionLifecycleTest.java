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
 * 실제 Spring 컨텍스트를 닫아 ContextClosedEvent와 조정기의 연결 정리 위임을 확인한다.
 * 서비스 메서드를 직접 호출하는 단위 테스트와 달리 EventListener 등록까지 검증한다.
 * emitter·저장소·조정기는 Mock이므로 실제 HTTP 응답 종료나 저장소 삭제 완료를 검증하지는 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 연결 종료 생명주기")
public class SseConnectionLifecycleTest {

    @Mock
    SseEmitterRepository repository;

    @Mock
    private SseDeliveryCoordinator deliveryCoordinator;

    @Test
    @DisplayName("컨텍스트가 종료되면 에미터 종료를 요청하고 연결 정리를 조정기에 위임한다.")
    void contextCloseRequestsCompletionAndDelegatesCleanup(){
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
                    ()-> new SseConnectionService(repository, properties, deliveryCoordinator));

            context.refresh();
            verify(emitter, never()).complete();
            verify(deliveryCoordinator, never()).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }

        verify(emitter).complete();
        verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());

    }

    @Test
    @DisplayName("Lifecycle 빈이 종료되기 전에 SSE 에미터 종료를 요청하고 연결 정리를 위임한다.")
    void contextCloseDelegatesCleanupBeforeLifecycleStop(){
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
                    ()-> new SseConnectionService(repository, properties, deliveryCoordinator));
            context.registerBean("testLifecycle", Lifecycle.class, () -> lifecycle);
            context.refresh();

        }

        // 일반 Lifecycle.stop보다 연결 정리 요청이 먼저라는 순서를 검증한다.
        // 스케줄러도 ContextClosedEvent를 받을 수 있으므로 모든 하트비트가 이미 멈췄다고 보장하지는 않는다.
        InOrder completionOrder = inOrder(emitter, lifecycle);
        completionOrder.verify(emitter).complete();
        completionOrder.verify(lifecycle).stop();

        InOrder cleanupOrder = inOrder(deliveryCoordinator, lifecycle);
        cleanupOrder.verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
        cleanupOrder.verify(lifecycle).stop();
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }
}
