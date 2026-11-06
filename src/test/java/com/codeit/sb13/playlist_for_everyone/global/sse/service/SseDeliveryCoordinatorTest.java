package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 복구·실시간 전송 조정")
class SseDeliveryCoordinatorTest {

    @Mock
    private SseRecoveryService recoveryService;

    @Mock
    private SseEmitter emitter;

    private SseDeliveryCoordinator coordinator;
    private final UUID userId = UUID.randomUUID();
    private final Connection connection = Connection.create();
    private final UUID lastEventId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        coordinator = new SseDeliveryCoordinator(recoveryService);
    }

    @Test
    @DisplayName("등록한 사용자·연결·에미터와 마지막 이벤트 ID로 복구를 요청한다.")
    void recoverDelegatesWithRegisteredConnectionAndLastEventId(){
        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        verify(recoveryService).recover(eq(userId), eq(connection), same(emitter), eq(lastEventId));
    }

    @Test
    @DisplayName("마지막 이벤트 ID가 없어도 등록한 연결의 복구를 요청한다.")
    void recoverDelegatesWithoutLastEventId(){
        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, null);

        verify(recoveryService)
                .recover(eq(userId), eq(connection), same(emitter), isNull());
    }

    @Test
    @DisplayName("같은 사용자의 두 연결은 각자의 에미터와 마지막 이벤트 ID로 복구를 요청한다.")
    void recoversConnectionsOfSameUserIndependently(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        verify(recoveryService)
                .recover(eq(userId), eq(connection), same(emitter), eq(lastEventId));
        verify(recoveryService)
                .recover(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondLastEventId));
    }

    @Test
    @DisplayName("제거한 연결은 복구하지 않고 같은 사용자의 다른 연결은 복구한다.")
    void removePreventsRecoveryOnlyForRemovedConnection(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.remove(userId, connection);
        coordinator.recover(userId, connection, lastEventId);

        coordinator.recover(userId, secondConnection, secondLastEventId);

        verify(recoveryService)
                .recover(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondLastEventId));

        verifyNoMoreInteractions(recoveryService);
    }
}