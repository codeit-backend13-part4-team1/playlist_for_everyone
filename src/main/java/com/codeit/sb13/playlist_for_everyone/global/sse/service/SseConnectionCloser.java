package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 조정기가 상태·예약을 정리한 뒤 HTTP 종료 요청과 emitter 저장소 삭제를 수행하는 컴포넌트다.
 * 일반 전송 실패와 예약 작업 실패·재시도 소진에서는 close, 종료 콜백의 참조 정리에서는 cleanUp을 사용한다.
 * 이 컴포넌트는 조정기 상태를 직접 변경하지 않으며 Redis 공유 이력도 삭제하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseConnectionCloser {

    private final SseEmitterRepository sseEmitterRepository;

    /**
     * 전달받은 HTTP 응답에 종료를 요청하고, 성공 여부와 관계없이 저장소 참조를 제거한다.
     * emitter는 조정기가 저장소에서 확보한 실제 종료 대상이다. 삭제 시 객체 동일성을 비교하는 용도가 아니다.
     * complete의 실패는 로그로 남겨 다른 연결의 처리를 막지 않으며, 완료 콜백 실행을 기다리지 않는다.
     */
    public void close(UUID userId, Connection connection, SseEmitter emitter){
        try {
            emitter.complete();
        } catch (RuntimeException e){
            log.warn(
                    "SSE 연결 종료 요청에 실패했습니다. userId={}, connectionId={}",
                    userId,
                    connection.id(),
                    e
            );
        } finally {
            cleanUp(userId, connection);
        }
    }

    /**
     * 재사용하지 않는 사용자·연결 키로 로컬 참조만 삭제한다. HTTP 종료를 추가로 요청하지 않는다.
     * 조정기 상태·예약 정리는 호출 전에 끝나 있어야 하므로 운영 호출은 조정기를 통해 들어온다.
     */
    public void cleanUp(UUID userId, Connection connection){
        sseEmitterRepository.delete(userId, connection);
    }
}
