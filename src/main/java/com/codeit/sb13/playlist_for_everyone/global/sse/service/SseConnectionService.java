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
 * 연결: JWT 인증 -> SseController -> connect -> 콜백 등록 -> 저장 -> 최초 주석 전송
 * 전송: SseDeliveryService -> 저장소에서 수신자의 연결 조회 -> 각 emitter로 전송
 * 종료: 완료/타임아웃/오류 콜백 또는 애플리케이션 종료 -> 해당 연결 정리
 * </pre>
 *
 * <p>처음 읽을 때는 SseController, 이 클래스, SseEmitterRepository와 메모리 구현체,
 * SseDeliveryService, SseHeartbeatScheduler 순서로 연결 흐름을 따라가면 된다.
 * 서버 간 메시지 발행은 별도의 SseEventPublisher 계약이며, 현재 구현은 로컬 연결을 다룬다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseConnectionService {

    private final SseEmitterRepository sseEmitterRepository;
    private final SseProperties sseProperties;

    /**
     * 새 연결을 등록하고, 응답 시작을 위한 ID 없는 주석을 준비한다.
     *
     * @param userId 인증된 사용자 ID
     * @return 이번 연결에서 생성한 emitter
     * @throws UncheckedIOException 초기 send 호출에서 I/O 예외가 발생한 경우. 원인을 보존한다
     * @throws IllegalStateException 초기 send 호출에서 emitter 상태 예외가 발생한 경우
     */
    public SseEmitter connect(UUID userId){
        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter(sseProperties.connectionTimeout().toMillis());
        // 여기서는 콜백을 등록할 뿐 실행하지 않는다. 실제 완료/타임아웃/오류 시 MVC가 호출한다.
        // 등록 당시의 userId와 connection을 캡처하여, 이전 연결 종료가 새 연결을 지우지 않게 한다.
        emitter.onCompletion(()-> sseEmitterRepository.delete(userId, connection));
        emitter.onTimeout(()-> {
            sseEmitterRepository.delete(userId, connection);
            // 종료 후 onCompletion이 다시 삭제를 요청해도 저장소의 중복 삭제는 안전하다.
            emitter.complete();
        });
        emitter.onError(e -> {
            // 오류가 발생한 연결의 참조를 제거한다. 오류에 따른 비동기 응답 종료는 MVC가 관리한다.
            sseEmitterRepository.delete(userId, connection);
            logSseConnectionIssue(userId, e, connection);
        });

        // 전송을 시도하기 전에 저장하여 이후 콜백과 전송 실패 처리가 같은 연결을 정리하게 한다.
        sseEmitterRepository.save(userId, connection, emitter);

        try {
            // 주석은 업무 이벤트가 아니므로 ID를 넣지 않아 클라이언트의 마지막 이벤트 ID를 유지한다.
            // 컨트롤러 반환 전에는 MVC의 전송 handler가 없어 데이터가 버퍼에 보관될 수 있다.
            // 반환 후 MVC가 emitter를 초기화하면 보관한 주석을 실제 응답에 쓰고 응답을 시작한다.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            handleConnectionError(userId, e, connection);
        }
        return emitter;
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
                            // 콜백 실행 여부나 complete 성공 여부에 의존하지 않고 저장소 참조를 정리한다.
                            sseEmitterRepository.delete(userId, connection);
                        }
                    });
                });
    }

    /**
     * connect 내부에서 잡은 두 종류의 초기 전송 예외를 정리한 뒤 호출자에게 전달한다.
     * 연결 중 계속 발생하는 전송 실패는 SseDeliveryService와 등록된 오류 콜백에서 처리한다.
     */
    private void handleConnectionError(UUID userId, Exception e, Connection connection) {
        sseEmitterRepository.delete(userId, connection);
        if (e instanceof IOException io){
            throw new UncheckedIOException("SSE 초기 주석 전송에 실패했습니다.", io);
        }
        if (e instanceof IllegalStateException illegalState){
            throw illegalState;
        }
    }
}
