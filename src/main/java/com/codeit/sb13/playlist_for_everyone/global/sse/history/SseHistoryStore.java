package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * 서버 간에 공유하는 이벤트 이력 계약. HTTP 연결은 별도의 로컬 저장소가 관리한다.
 *
 * <p>클라이언트 커서는 이벤트 UUID, 서버의 처리 위치는 SseHistoryPosition이다.
 * 보관 범위 밖의 이벤트는 복구하지 못하며, 조회 실패와 정상적인 빈 결과를 구분한다.
 * Redis 구현은 저장·조회 시 오래된 이벤트와 매핑을 함께 정리한다.
 */
public interface SseHistoryStore {

    /**
     * 수신자별로 저장한다. 보관 중인 동일 UUID·내용은 기존 위치와 이벤트를 반환한다.
     * 같은 UUID의 내용이나 이벤트 이름이 다르면 충돌 예외를 발생시키며 덮어쓰지 않는다.
     * 중복 방지는 현재 보관 중인 이력에 한정되며, 수신자가 다르면 독립적으로 저장한다.
     */
    SseHistoryEntry append(SseEvent<JsonNode> event);

    /**
     * 조회 시점의 끝 위치와 본인 커서 이후 목록을 함께 확보한다.
     * 커서가 없거나 본인 이력에서 찾을 수 없으면 현재 끝 위치와 빈 목록을 반환한다.
     * 전체 이력이 없으면 끝 위치도 null이다. 조회가 키의 TTL을 연장하지는 않는다.
     */
    SseHistorySnapshot snapshot(UUID receiverId, UUID lastEventId);

    /**
     * 기준 위치를 제외한 후속 이력을 저장 순서대로 반환한다. null이면 남은 전체 이력이다.
     * 기준 위치가 사라졌어도 남은 후속 이력은 반환하며 gapDetected만 true로 표시한다.
     * 기준 위치가 null이면 gapDetected는 false다. 누락 표시는 조회 실패를 뜻하지 않는다.
     */
    SseHistoryReadResult readAfter(UUID receiverId, SseHistoryPosition afterPosition);
}
