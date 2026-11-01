package com.codeit.sb13.playlist_for_everyone.global.sse.repository;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * 현재 서버에 열린 SSE 연결의 등록·조회·제거 계약이다.
 * 사용자마다 여러 연결을 허용하며, 제거 대상은 사용자 ID와 연결 ID의 조합으로 지정한다.
 *
 * <p>조회 결과의 Map 구조는 수정할 수 없는 사본이다. 내부 SseEmitter는 복제하지 않으므로
 * 조회 이후 실제 연결 상태가 바뀔 수 있다. 이 저장소는 메시지 이력이나 브로커 저장소가 아니다.
 */
public interface SseEmitterRepository {
    /** 사용자에게 속한 연결과 실제 HTTP 응답에 사용할 emitter를 등록한다. */
    void save(UUID userId, Connection connection, SseEmitter sseEmitter);

    /** 해당 사용자의 연결 사본을 반환하며, 연결이 없으면 빈 Map을 반환한다. */
    Map<Connection, SseEmitter> findAllByUserId(UUID userId);

    /** 지정한 연결만 제거한다. 이미 제거됐거나 없는 연결을 다시 제거해도 다른 연결에 영향이 없다. */
    void delete(UUID userId, Connection connection);

    /**
     * 하트비트·애플리케이션 종료 처리에 사용할 전체 연결 사본을 반환한다.
     * 바깥 Map과 사용자별 Map 모두 수정할 수 없으며, 모든 사용자의 동일 시점 상태를 보장하지 않는다.
     */
    Map<UUID,  Map<Connection, SseEmitter>> findAll();
}
