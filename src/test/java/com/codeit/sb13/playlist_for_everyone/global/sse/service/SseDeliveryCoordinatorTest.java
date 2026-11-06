package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("복구가 완료된 위치부터 해당 연결의 후속 전송을 요청한다.")
    void requestsDeliveryAfterRecoveredPosition(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService).recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
    }

    @Test
    @DisplayName("복구 완료 위치가 null이어도 해당 연결의 후속 전송을 요청한다.")
    void requestsDeliveryWhenRecoveredPositionIsNull(){
        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(null);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), isNull());
    }

    @Test
    @DisplayName("후속 전송이 반환한 위치를 다음 전송 요청의 기준으로 사용한다.")
    void usesUpdatedPositionForNextDeliveryRequest(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, connection, emitter,lastEventId))
                .thenReturn(upperBound);

        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(nextPosition);

        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);

        ArgumentCaptor<SseHistoryPosition> positionCaptor = ArgumentCaptor.forClass(SseHistoryPosition.class);
        verify(recoveryService, times(2)).recoverAfter(
                eq(userId),
                eq(connection),
                same(emitter),
                positionCaptor.capture()
        );

        assertThat(positionCaptor.getAllValues())
                .containsExactly(upperBound, nextPosition);
    }

    @Test
    @DisplayName("같은 사용자의 두 연결은 후속 전송위치를 독립적으로 관리한다.")
    void maintainsIndependentDeliveryPositionsForConnectionOfSameUser(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition firstNextPosition = createHistoryPosition("1000-1");
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(firstPosition);

        when(recoveryService.recover(userId, secondConnection, secondEmitter, secondLastEventId))
                .thenReturn(secondPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, firstPosition))
                .thenReturn(firstNextPosition);
        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, secondPosition))
                .thenReturn(secondPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection,lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, secondConnection);

        verify(recoveryService).recoverAfter(eq(userId), eq(connection), same(emitter), eq(firstPosition));
        verify(recoveryService).recoverAfter(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondPosition));

    }

    @Test
    @DisplayName("연결 등록 후 복구를 시작하기 전에는 후속 전송을 즉시 실행하지 않는다.")
    void doesNotDeliverBeforeRecoveryStarts(){
        coordinator.register(userId, connection, emitter);
        coordinator.requestDelivery(userId, connection);

        verifyNoInteractions(recoveryService);
    }

    @Test
    @DisplayName("제거한 연결은 후속 전송하지 않고 같은 사용자의 다른 연결은 전송한다.")
    void doesNotDeliverToRemovedConnectionAndKeepsOtherConnection(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(firstPosition);

        when(recoveryService.recover(userId, secondConnection, secondEmitter, secondLastEventId))
                .thenReturn(secondPosition);

        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, secondPosition))
                .thenReturn(secondPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        coordinator.remove(userId, connection);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, secondConnection);

        verify(recoveryService, never() )
                .recoverAfter(eq(userId), eq(connection), any(), any());

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondPosition));

    }

    @Test
    @DisplayName("복구 전에 받은 전송 요청은 복구 완료 후 해당 위치부터 처리한다.")
    void deliversPendingRequestAfterRecoveryCompletes(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.requestDelivery(userId, connection);

        verifyNoInteractions(recoveryService);

        coordinator.recover(userId, connection, lastEventId);

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
    }

    @Test
    @DisplayName("복구 전에 받은 여러 전송 요청은 복구 완료 후 한 번으로 합쳐 처리한다.")
    void coalescesRequestsBeforeRecoveryIntoSingleDelivery(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);

        verifyNoInteractions(recoveryService);

        coordinator.recover(userId, connection, lastEventId);
        verify(recoveryService).recover(userId, connection, emitter, lastEventId);
        verify(recoveryService).recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("복구 진행 중 받은 전송 요청은 복구 완료 후 처리한다.")
    void deliversRequestReceivedDuringRecoveryAfterCompletion(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenAnswer(invocation ->{
                    coordinator.requestDelivery(userId, connection);
                    verify(recoveryService, never())
                            .recoverAfter(any(), any(),any(),any());

                    return upperBound;
                });

        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
    }

    @Test
    @DisplayName("복구가 실패하면 보류한 전송 요청을 실행하지 않고 원래 예외를 전달한다.")
    void propagatesRecoveryFailureWithoutDeliveringPendingRequest(){
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 스냅샷 복구 실패");

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.requestDelivery(userId, connection);

        assertThatThrownBy(() -> coordinator.recover(userId, connection, lastEventId))
                .isSameAs(failure);

        verify(recoveryService)
                .recover(userId, connection, emitter, lastEventId);
        verify(recoveryService, never()).recoverAfter(any(), any(), any(), any());
    }

    @Test
    @DisplayName("후속 전송 중 받은 요청은 현재 전송 완료 후 갱신된 위치부터 처리한다.")
    void deliversRequestReceivedDuringDeliveryAfterCurrentDeliveryCompletes(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    verify(recoveryService, times(1))
                            .recoverAfter(any(), any(), any(), any());
                    return nextPosition;
                })
                .thenReturn(nextPosition);

        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        ArgumentCaptor<SseHistoryPosition> positionCaptor = ArgumentCaptor.forClass(SseHistoryPosition.class);

        verify(recoveryService, times(2))
                .recoverAfter(eq(userId), eq(connection), same(emitter), positionCaptor.capture());
        assertThat(positionCaptor.getAllValues())
                .containsExactly(upperBound, nextPosition);
    }

    @Test
    @DisplayName("후속 전송 중 받은 여러 요청은 현재 전송 완료 후 한 번으로 합쳐 처리한다.")
    void coalescesRequestsDuringDeliveryIntoSingleFollowingDelivery(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    coordinator.requestDelivery(userId, connection);
                    coordinator.requestDelivery(userId, connection);
                    verify(recoveryService, times(1))
                            .recoverAfter(any(), any(), any(), any());
                    return nextPosition;
                })
                .thenReturn(nextPosition);

        when(recoveryService. recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recover(userId, connection, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter),eq(nextPosition));
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("후속 전송이 실패하면 보류한 요청을 실행하지 않고 원래 예외를 전달한다.")
    void propagatesDeliveryFailureWithoutDeliveringPendingRequest(){
        SseHistoryPosition upperBound = createHistoryPosition();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    throw failure;
                })
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatThrownBy(() -> coordinator.requestDelivery(userId, connection))
                .isSameAs(failure);

        verify(recoveryService)
                .recover(userId, connection, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("후속 전송 중 연결이 제거되면 보류한 요청을 실행하지 않는다.")
    void stopsPendingDeliveryWhenConnectionIsRemovedDuringDelivery(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, connection,emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation ->{
                    coordinator.requestDelivery(userId, connection);
                    coordinator.remove(userId, connection);
                    return nextPosition;
                });

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recover(userId, connection, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("초기 복구 중 연결이 제거되면 복구 완료 후 보류한 요청을 실행하지 않는다.")
    void  stopsPendingDeliveryWhenConnectionIsRemovedDuringRecovery(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, connection, emitter, lastEventId))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    coordinator.remove(userId, connection);
                    return upperBound;
                });

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recover(eq(userId), eq(connection), same(emitter), eq(lastEventId));
        verify(recoveryService, never())
                .recoverAfter(any(), any(), any(), any());
        verifyNoMoreInteractions(recoveryService);
    }

    private SseHistoryPosition createHistoryPosition(String position) {
        return new SseHistoryPosition(position);
    }

    private SseHistoryPosition createHistoryPosition() {
        return createHistoryPosition("1000-0");
    }

}