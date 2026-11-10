package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.*;
import com.codeit.sb13.playlist_for_everyone.global.sse.replay.SseReplayFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 공유 이력을 조회하고 도메인 필터를 거쳐 한 emitter에 순서대로 전송한다.
 * 연결 상태와 재시도 예약은 SseDeliveryCoordinator가 담당하며 이 서비스는 처리 위치를 반환한다.
 * 조회 목록은 메서드 실행 중에만 참조한다. 장기 보관이나 브로커 재발행은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseRecoveryService {

    private final SseHistoryStore historyStore;
    private final List<SseReplayFilter> replayFilters;

    /**
     * 한 번 확보한 스냅샷을 필터링하여 해당 emitter로 전송하고 후속 조회 기준을 반환한다.
     * 커서가 null이거나 보관 이력에 없으면 과거 재전송 없이 끝 위치만 확보한다. 연결 상태는 조정기가 관리한다.
     *
     * @param lastEventId SSE 프레임의 이벤트 UUID. DB 알림 ID나 Redis Stream ID가 아니다
     * @return 필터 적용 전 원본 끝 위치. 모든 항목이 제외돼도 유지하며, 보관 이력 자체가 없으면 null
     */
    public SseHistoryPosition recover(
            UUID userId,
            SseEmitter emitter,
            UUID lastEventId
    ){
        SseHistorySnapshot snapshot = historyStore.snapshot(userId, lastEventId);
        filterReplayEntries(userId, snapshot.entries())
                .forEach(entry ->
                        sendEvent(emitter, entry.event()));

        return snapshot.upperBound();
    }

    /**
     * 기준 위치가 없어졌어도 남아 있는 후속 이력을 전송한다. 빈 결과면 기존 위치를 유지한다.
     * gapDetected 자체를 예외로 바꾸지 않으므로 연결 종료나 조회 재시도를 유발하지 않는다.
     * 필터에서 제외한 항목도 원본 마지막 위치까지 처리한 것으로 반환한다.
     *
     * @param connection 기준 위치 누락 로그에서 연결을 식별하는 값. 이력 조회는 사용자 ID 기준이다
     * @param afterPosition 마지막으로 처리한 내부 저장 위치. null이면 남아 있는 전체 이력을 조회한다
     * @return 원본 마지막 위치. 원본 조회가 비었으면 기존 위치를 그대로 반환한다
     */
    public SseHistoryPosition recoverAfter(
            UUID userId,
            Connection connection,
            SseEmitter emitter,
            SseHistoryPosition afterPosition
    ){
        SseHistoryReadResult result = historyStore.readAfter(userId, afterPosition);

        if (result.gapDetected()){
            log.debug(
                    """
                    SSE 기준 위치가 보관 이력에 없어 남은 후속 이력을 처리합니다.
                    userId={}, connectionId={}, afterPosition={}, remainingCount={}
                    """,
                    userId,
                    connection.id(),
                    afterPosition,
                    result.entries().size()
            );
        }
        List<SseHistoryEntry> entries = result.entries();

        if (entries.isEmpty()) {
            return afterPosition;
        }

        filterReplayEntries(userId, entries)
                .forEach(entry -> sendEvent(emitter, entry.event()));

        // 제외된 항목도 지나가야 같은 잘못된 데이터나 삭제된 알림을 다음 신호마다 다시 읽지 않는다.
        return entries.get(entries.size() - 1).position();
    }

    /**
     * 저장된 이벤트 UUID·이름·본문으로 SSE 프레임을 구성한다. 새 이벤트 ID를 발급하지 않는다.
     * send 성공은 브라우저 수신 확인이 아니다. 실패하면 이 복구 호출을 중단하고 원래 원인을 호출자에게 전달한다.
     */
    private void sendEvent(SseEmitter emitter, SseEvent<JsonNode> event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.eventId().toString())
                    .name(event.eventName())
                    .data(event.data(), MediaType.APPLICATION_JSON)
            );
        } catch (IOException e) {
            throw new UncheckedIOException("SSE 복구 이벤트 전송에 실패했습니다.",e);
        }
    }

    /**
     * 이벤트 종류별 필터 조회를 모두 끝낸 뒤 원본 순서로 돌려준다.
     * 필터 조회 실패 전에 일부 이벤트를 전송하는 것을 막는다. 필터 예외는 그대로 전달한다.
     * 지원 필터가 없는 종류는 유지한다. 허용 위치를 Set으로 모아도 전송 순서는 원본 목록을 따른다.
     * 같은 이름을 지원하는 필터가 여러 개면 주입 목록의 첫 번째만 사용하며 연쇄 적용하지 않는다.
     */
    private List<SseHistoryEntry> filterReplayEntries(UUID receiverId, List<SseHistoryEntry> entries){
        if (entries.isEmpty() || replayFilters.isEmpty()){
            return entries;
        }

        LinkedHashMap<String, List<SseHistoryEntry>> groupedEntries = entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.event().eventName(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Set<SseHistoryPosition> allowedPositions = new HashSet<>();
        for (Map.Entry<String, List<SseHistoryEntry>> group : groupedEntries.entrySet()){
            SseReplayFilter replayFilter = replayFilters.stream()
                    .filter(filter -> filter.supports(group.getKey()))
                    .findFirst()
                    .orElse(null);

            List<SseHistoryEntry> candidates = group.getValue();
            List<SseHistoryEntry> retainedEntries = replayFilter == null ?
                    candidates : replayFilter.filter(receiverId, candidates);

            retainedEntries.forEach(entry -> allowedPositions.add(entry.position()));
        }
        return entries.stream()
                .filter(entry -> allowedPositions.contains(entry.position()))
                .toList();
    }

}
