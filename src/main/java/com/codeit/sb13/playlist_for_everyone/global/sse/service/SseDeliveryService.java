package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 현재 서버의 메모리에 등록된 연결로 업무 이벤트와 하트비트를 전송한다.
 *
 * <p>사용자의 탭이 다른 서버에도 연결될 수 있으므로 이 서비스 호출만으로 모든 서버에
 * 이벤트가 전달되지는 않는다. 후속 Redis/Kafka 수신 구현체가 각 서버에서 이 서비스를 호출한다.
 * 연결 생성은 SseConnectionService, 연결 참조 관리는 SseEmitterRepository가 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseDeliveryService {

    private final SseEmitterRepository sseEmitterRepository;

    /**
     * 수신자의 모든 로컬 연결에 같은 논리적 이벤트를 전달한다.
     * 연결이 없으면 별도 저장이나 재시도 없이 종료한다.
     *
     * @param sseEvent 수신자와 이벤트 ID, 이름 및 JSON으로 직렬화할 payload를 담은 이벤트
     */
    public void sendToLocalConnections(SseEvent<?> sseEvent){
        Map<Connection, SseEmitter> connections = sseEmitterRepository.findAllByUserId(sseEvent.receiverId());

        // 조회한 Map은 전송 대상을 순회하기 위한 사본이다. 조회 후 종료된 emitter도 있을 수 있다.
        connections.forEach((connection, emitter)->{
            try {
                // SseEvent 전체가 아닌 data만 메시지 변환기로 직렬화한다. JSON 문자열을 미리 만들지 않는다.
                // 연결 ID와 이벤트 ID는 별개이며, 여러 탭에 보내도 동일 이벤트의 id는 유지한다.
                emitter.send(
                        SseEmitter.event()
                        .id(sseEvent.eventId().toString())
                        .name(sseEvent.eventName())
                        .data(sseEvent.data()
                        )
                );
            } catch (IOException | IllegalStateException e) {
                // 실패한 연결만 전송 대상에서 제외하고 다음 연결을 계속 처리한다.
                // 아직 열리지 않은 연결 요청의 실패를 호출자에게 전파하는 connect와 처리 목적이 다르다.
                sseEmitterRepository.delete(sseEvent.receiverId(), connection);
                logSseEventDeliveryIssue(sseEvent, connection, e);
            }
        });

    }

    /**
     * 모든 로컬 연결에 ID 없는 주석을 보내고, 전송 중 확인된 실패 연결을 정리한다.
     * 상대방의 응답을 받는 ping/pong 방식이 아니므로 브라우저 종료를 즉시 확정하는 기능은 아니다.
     */
    public void sendHeartbeatToLocalConnections(){
        sseEmitterRepository.findAll()
                .forEach((userId, connections) ->
                        connections.forEach((connection, emitter) ->
                                sendHeartbeat(userId, connection, emitter)
                        )
                );
    }

    private void sendHeartbeat(UUID userId, Connection connection, SseEmitter emitter) {
        try {
            // 주석 프레임은 알림/DM 이벤트로 전달되지 않으며 마지막 수신 이벤트 ID도 변경하지 않는다.
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            sseEmitterRepository.delete(userId, connection);
            logSseHeartbeatIssue(userId, connection, e);
        }
    }

    /**
     * 하트비트 전송 중 Spring이 식별한 클라이언트 단절은 DEBUG로만 남긴다.
     * 모든 I/O 예외가 단절은 아니므로, 나머지 실패는 원인을 확인할 수 있게 WARN에 스택을 남긴다.
     */
    private void logSseHeartbeatIssue(UUID userId, Connection connection, Exception e) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)){
            log.debug("SSE 하트비트 전송 중 클라이언트 단절로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id());
            return;
        }
        log.warn("SSE 하트비트 전송 실패로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id(), e);
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
