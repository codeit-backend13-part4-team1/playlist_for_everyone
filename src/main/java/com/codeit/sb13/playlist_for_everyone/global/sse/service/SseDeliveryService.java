package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.ConnectionKey;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.DisconnectedClientHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 현재 서버의 메모리에 등록된 연결로 업무 이벤트와 하트비트를 전송한다.
 *
 * <p>사용자의 탭이 다른 서버에도 연결될 수 있으므로 이 서비스 호출만으로 모든 서버에
 * 이벤트가 전달되지는 않는다. Redis 수신 구현체가 각 서버에서 이 서비스를 호출한다.
 * 연결 생성은 SseConnectionService, 연결 참조 관리는 SseEmitterRepository가 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseDeliveryService {

    private final SseEmitterRepository sseEmitterRepository;

    private final SseDeliveryCoordinator deliveryCoordinator;

    private final UserSessionRepository userSessionRepository;

    private final SseHeartbeatDispatcher heartbeatDispatcher;

    private final AtomicInteger nextHeartbeatStartIndex = new AtomicInteger();

    /**
     * 수신자의 모든 로컬 연결에 후속 이력 조회를 요청한다. 브로커 메시지는 조회를 깨우는 신호다.
     * 실제 전송할 데이터와 순서는 공유 이력에서 가져오며, 이 메서드가 payload를 직접 보내지는 않는다.
     * 연결이 없으면 정상 종료한다. 여기서 이력을 저장하거나 브로커에 재발행하지 않는다.
     *
     * <p>일시적 조회 실패의 예약 재시도는 coordinator가 처리한다. 그 밖에 전달된 RuntimeException은
     * 조정기에 해당 연결의 종료와 정리를 요청하고 다음 연결로 진행한다. 조회·필터 오류는 HTTP 단절을 뜻하지
     * 않으므로 참조만 지워 전송 불가능한 응답이 계속 열린 채 남지 않도록 complete도 요청한다.
     *
     * @param sseEvent 수신자 식별과 실패 로그의 이벤트 ID 추적에 사용하는 전송 신호
     */
    public void sendToLocalConnections(SseEvent<?> sseEvent){
        UUID receiverId = sseEvent.receiverId();
        Map<Connection, SseEmitter> connections = sseEmitterRepository.findAllByUserId(receiverId);

        if (connections.isEmpty()){
            return;
        }

        // 사본에서는 연결 키만 순회한다. 실제 전송할 emitter는 조정기가 저장소에서 다시 조회한다.
        // 사본 조회 후 연결이 사라질 수 있으며, 이 순회가 연결별 병렬 실행을 의미하지는 않는다.
        connections.keySet().forEach(connection -> {
            try {
                // 각 연결은 자신의 위치부터 읽는다. 신호가 중복되거나 역순이어도 이력 순서가 기준이다.
                deliveryCoordinator.requestDelivery(receiverId, connection);
            } catch (RuntimeException e) {
                // 조정기 상태·예약을 먼저 정리한 뒤 HTTP 종료와 저장소 삭제를 요청한다.
                // 아직 열리지 않은 연결 요청의 실패를 호출자에게 전파하는 connect와 처리 목적이 다르다.
                deliveryCoordinator.closeConnection(receiverId, connection);
                logSseEventDeliveryIssue(sseEvent, connection, e);
            }
        });

    }

    /**
     * 모든 로컬 연결의 하트비트 전송을 전용 실행기에 제출한다.
     * 상대방의 응답을 받는 ping/pong 방식이 아니므로 브라우저 종료를 즉시 확정하는 기능은 아니다.
     */
    public void sendHeartbeatToLocalConnections(){
        heartbeatDispatcher.retryPendingClosures();
        List<ConnectionKey> connections = new ArrayList<>();
        sseEmitterRepository.findAll()
                .forEach((userId, emitters) ->
                        emitters.keySet()
                                .forEach(connection ->
                                        connections.add(new ConnectionKey(userId, connection))));

        int connectionsCount = connections.size();
        if (connectionsCount == 0){
            return;
        }

        int index = nextHeartbeatStartIndex.getAndUpdate(previous -> (previous + 1) % connectionsCount) % connectionsCount;

        for (int submitted = 0; submitted < connectionsCount; submitted++) {
            ConnectionKey key = connections.get(index);

            heartbeatDispatcher.submit(key.userId(), key.connection());
            index = (index + 1) % connectionsCount;
        }
    }

    public void revalidateLocalSessions(){
        sseEmitterRepository.findAllLoginSessions()
                .forEach((userId, value) -> value
                        .stream()
                        .filter(loginSession -> isNotCurrentSession(userId, loginSession))
                        .forEach(loginSession -> closeRevokedLocalConnections(userId, loginSession)));
    }

    private boolean isNotCurrentSession(UUID userId, LoginSession loginSession) {
        try {
            return !userSessionRepository.isCurrent(userId, loginSession.id());
        }catch (DataAccessException e){
            log.warn("SSE 세션 재검증 중 Redis 조회에 실패했습니다. userId={}", userId, e);
            return false;
        }
    }

    public void revoke(UUID userId, LoginSession loginSession){
        sseEmitterRepository.findByUserIdAndLoginSessionId(userId, loginSession)
                .keySet()
                .forEach(connection ->
                        deliveryCoordinator.closeConnection(userId, connection));
    }

    private void closeRevokedLocalConnections(UUID userId, LoginSession loginSession) {
        sseEmitterRepository.findByUserIdAndLoginSessionId(userId, loginSession)
                .keySet()
                .forEach(connection -> {
                    deliveryCoordinator.stopDelivery(userId, connection);
                    heartbeatDispatcher.requestClose(userId, connection);
                });
    }

    /**
     * 업무 이벤트 전송도 하트비트와 같은 단절 분류 및 DEBUG/WARN 기준을 사용한다.
     * 로그 수준과 관계없이 호출부는 실패한 연결을 이미 제거했으며, 다음 연결의 전송을 계속한다.
     */
    private void logSseEventDeliveryIssue(SseEvent<?> sseEvent, Connection connection, Exception e) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)){
            log.debug("SSE 클라이언트 단절로 연결을 정리했습니다. eventId={}, receiverId={}, connectionId={}"
                    , sseEvent.eventId(), sseEvent.receiverId(), connection.id());
            return;
        }
        log.warn("SSE 이벤트 전송 실패로 연결을 정리했습니다. eventId={}, receiverId={}, connectionId={}"
                , sseEvent.eventId(), sseEvent.receiverId(), connection.id(), e);
    }
}
