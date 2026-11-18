package com.codeit.sb13.playlist_for_everyone.global.sse.repository;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 현재 서버에 열린 SSE 연결의 등록·조회·제거 계약이다.
 * 사용자마다 여러 연결을 허용하며, 제거 대상은 사용자 ID와 연결 ID의 조합으로 지정한다.
 * 재연결은 새 연결 ID를 사용한다. 저장소는 현재 등록된 키의 덮어쓰기를 거부하며, 삭제한 ID의 이력은 보관하지 않는다.
 *
 * <p>조회 결과의 Map 구조는 수정할 수 없는 사본이다. 내부 SseEmitter는 복제하지 않으므로
 * 조회 이후 실제 연결 상태가 바뀔 수 있다. 이 저장소는 메시지 이력이나 브로커 저장소가 아니다.
 */
public interface SseEmitterRepository {
    /**
     * 새 연결을 등록한다. 같은 emitter를 다시 전달해도 이미 등록된 사용자·연결 키라면 거부한다.
     *
     * @throws IllegalStateException 같은 키가 등록되어 있는 경우. 기존 emitter는 유지한다
     */
    void save(UUID userId, Connection connection, SseEmitter sseEmitter);

    /**
     * 지정한 연결의 실제 emitter 참조를 반환하며, 사용자 또는 연결이 없으면 Optional.empty()를 반환한다.
     * 조회만으로 연결을 예약하거나 수명을 연장하지 않는다. 반환 이후 연결이 종료될 수 있다.
     */
    Optional<SseEmitter> findByUserIdAndConnection(UUID userId, Connection connection);

    /** 해당 사용자의 연결 사본을 반환하며, 연결이 없으면 빈 Map을 반환한다. */
    Map<Connection, SseEmitter> findAllByUserId(UUID userId);

    /**
     * 지정한 로컬 참조만 제거한다. HTTP 종료와 조정기 상태·예약 정리는 호출부가 담당한다.
     * 없는 연결의 중복 삭제는 무시한다. 호출자는 삭제한 연결 ID를 새 연결에 재사용하지 않아야 한다.
     */
    void delete(UUID userId, Connection connection);

    /**
     * 하트비트·애플리케이션 종료 처리에 사용할 전체 연결 사본을 반환한다.
     * 바깥 Map과 사용자별 Map 모두 수정할 수 없으며, 모든 사용자의 동일 시점 상태를 보장하지 않는다.
     */
    Map<UUID,  Map<Connection, SseEmitter>> findAll();
}
