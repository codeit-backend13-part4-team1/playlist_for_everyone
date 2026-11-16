package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * 현재 서버에 연결된 SSE 응답의 생성부터 종료까지를 관리한다.
 *
 * <p>SseEmitter는 한 HTTP 연결에 데이터를 보내는 객체다. 사용자 한 명에게 여러 연결이
 * 있을 수 있으므로 사용자 ID와 별도로 연결마다 Connection 식별자를 발급한다.
 *
 * <pre>
 * 연결: JWT 인증 -> connect -> 콜백/상태/연결 등록 -> 최초 주석 -> 초기 이력 복구
 * 전송: SseDeliveryService -> SseDeliveryCoordinator -> 후속 이력 조회/필터 -> emitter
 * 종료: 완료/타임아웃/오류 콜백 또는 애플리케이션 종료 -> 상태/예약/로컬 연결 정리
 * </pre>
 *
 * <p>이 클래스는 emitter를 생성하고 콜백을 연결한다. 상태와 저장소의 등록·정리는
 * SseDeliveryCoordinator에 위임한다. 저장소 직접 조회는 서버 종료 시 전체 연결을 찾는 데 사용한다.
 * 처음 읽을 때는 SseController → 이 클래스 → SseDeliveryCoordinator → SseRecoveryService 순서로 따라간다.
 * 서버 간 메시지 발행은 별도의 SseEventPublisher 계약이며, 현재 구현은 로컬 연결을 다룬다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseConnectionService {

    private final SseEmitterRepository sseEmitterRepository;
    private final SseProperties sseProperties;
    private final SseDeliveryCoordinator deliveryCoordinator;

    /**
     * 새 연결을 등록하고 ID 없는 주석과 초기 복구 데이터를 준비한다.
     * 응답 반환 전 복구하므로 조회 실패 시 SSE 응답 대신 JSON 오류를 반환할 수 있다.
     * 같은 사용자여도 매번 새 연결 ID를 발급한다. 이전 연결 ID를 재연결에 사용하지 않는다.
     *
     * @param userId 인증된 사용자 ID
     * @param lastEventId 마지막으로 받은 이벤트 UUID. null이면 과거 재전송 없이 현재 끝 위치에서 시작한다
     * @return 이번 연결에서 생성한 emitter
     * @throws UncheckedIOException 초기 주석 또는 복구 이벤트 전송 중 I/O 예외가 발생한 경우
     * @throws IllegalStateException 연결 중복 등록, 초기 전송 상태 또는 이력 해석에 문제가 있는 경우
     */
    public SseEmitter connect(UUID userId, UUID lastEventId){
        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter(sseProperties.connectionTimeout().toMillis());
        // 여기서는 콜백을 등록할 뿐 실행하지 않는다. 실제 완료/타임아웃/오류 시 MVC가 호출한다.
        // 등록 당시의 userId와 connection을 캡처하여, 이전 연결 종료가 새 연결을 지우지 않게 한다.
        emitter.onCompletion(()-> cleanupConnection(userId, connection));
        emitter.onTimeout(()-> {
            cleanupConnection(userId, connection);
            // 종료 후 onCompletion이 다시 삭제를 요청해도 저장소의 중복 삭제는 안전하다.
            emitter.complete();
        });
        emitter.onError(e -> {
            // 오류가 발생한 연결의 참조를 제거한다. 오류에 따른 비동기 응답 종료는 MVC가 관리한다.
            cleanupConnection(userId, connection);
            logSseConnectionIssue(userId, e, connection);
        });

        // 조정기가 상태 생성과 저장소 등록을 함께 담당한다. 저장 실패 시 자신이 만든 상태만 취소한다.
        // 중복 키의 기존 연결까지 정리하지 않도록 등록은 아래 전송 실패 처리의 try 밖에 둔다.
        deliveryCoordinator.register(userId, connection, emitter);
        try {
            // 주석은 업무 이벤트가 아니므로 ID를 넣지 않아 클라이언트의 마지막 이벤트 ID를 유지한다.
            // 컨트롤러 반환 전에는 MVC의 전송 handler가 없어 데이터가 버퍼에 보관될 수 있다.
            // 반환 후 MVC가 emitter를 초기화하면 보관한 주석을 실제 응답에 쓰고 응답을 시작한다.
            emitter.send(SseEmitter.event().comment("connected"));
            deliveryCoordinator.recover(userId, connection, lastEventId);
        } catch (IOException | RuntimeException e) {
            handleConnectionError(userId, e, connection);
        }
        return emitter;
    }

    /**
     * 애플리케이션 종료 이벤트를 받아 현재 서버의 연결에 종료를 요청한다.
     *
     * <p>스케줄러 자원 종료는 Spring이 담당한다. 이 이벤트를 받았다는 사실만으로
     * 이미 실행 중인 하트비트까지 모두 종료됐다고 가정하지 않는다.
     * complete 호출은 MVC의 비동기 종료 처리를 유도하며, 완료 콜백의 즉시 실행을 보장하지 않는다.
     */
    @EventListener(ContextClosedEvent.class)
    public void closeAllLocalConnections(){
        sseEmitterRepository.findAll()
                .forEach((userId , connections )->{
                    connections.forEach((connection, emitter)->{
                        try {
                            emitter.complete();
                        }catch (RuntimeException e){
                            // 한 연결의 종료 실패 때문에 다른 사용자의 연결 정리가 중단되지 않게 한다.
                            log.warn("SSE 연결 종료에 실패했습니다. userId={}, connectionId={}", userId, connection.id(),e);
                        }finally {
                            // 콜백 실행 여부나 complete 성공 여부에 의존하지 않고 상태·예약·저장소 참조를 정리한다.
                            cleanupConnection(userId, connection);
                        }
                    });
                });
    }

    /**
     * 초기 주석 전송 또는 이력 복구 실패 시 상태·예약·로컬 연결을 정리하고 원래 실패를 전달한다.
     * 등록에 성공한 연결만 이 경로에 들어온다. 생성 요청의 실패는 호출자에게 전달하여 HTTP 오류로 처리한다.
     * 연결 중 계속 발생하는 전송 실패는 SseDeliveryService와 등록된 오류 콜백에서 처리한다.
     */
    private void handleConnectionError(UUID userId, Exception e, Connection connection) {
        cleanupConnection(userId, connection);
        if (e instanceof IOException io){
            throw new UncheckedIOException("SSE 초기 주석 전송에 실패했습니다.", io);
        }
        if (e instanceof RuntimeException runtimeException){
            throw runtimeException;
        }
    }

    /**
     * onError 콜백에서 연결을 정리한 뒤 원인에 따라 로그를 남긴다.
     * Spring이 클라이언트 단절로 식별한 예외만 DEBUG로 기록하여 반복되는 단절 로그를 줄인다.
     * 모든 I/O 예외를 단절로 취급하지 않으며, 나머지는 원인 추적을 위해 ERROR에 스택을 남긴다.
     */
    private void logSseConnectionIssue(UUID userId, Throwable e, Connection connection) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)){
            log.debug("SSE 클라이언트 단절로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id());
            return;
        }
        log.error("SSE 연결 오류로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id(), e);
    }

    /**
     * 해당 연결의 상태·예약·저장소 참조 정리를 조정기에 위임한다. 여기서는 complete를 요청하지 않는다.
     * 연결 ID를 재사용하지 않으므로 이전 콜백이 반복되어도 새 연결이나 Redis 공유 이력을 삭제하지 않는다.
     */
    private void cleanupConnection(UUID userId, Connection connection){
        deliveryCoordinator.cleanupConnection(userId, connection);
    }
}
