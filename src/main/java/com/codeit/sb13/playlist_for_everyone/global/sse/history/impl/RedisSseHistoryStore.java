package com.codeit.sb13.playlist_for_everyone.global.sse.history.impl;

import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SseHistoryConflictException;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 사용자별 Stream과 이벤트 UUID 매핑을 Lua로 함께 갱신하는 공유 이력 저장소.
 * {사용자UUID} 해시 태그로 두 키의 Redis Cluster 슬롯을 맞춘다. 서로 다른 환경은 접두사로 구분한다.
 * Lua의 배열 응답은 내부 record로 검사한 뒤 공통 계약으로 변환하며, 비정상 응답을 빈 결과로 숨기지 않는다.
 * 조회 중에도 보관기간이 지난 이벤트를 정리하므로 조회가 Redis 데이터 변경을 전혀 하지 않는 것은 아니다.
 */
@Repository
public class RedisSseHistoryStore implements SseHistoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SseHistoryProperties historyProperties;
    private final RedisScript<List> appendScript;
    private final RedisScript<List> snapshotScript;
    private final RedisScript<List> readAfterScript;

    public RedisSseHistoryStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SseHistoryProperties historyProperties,
            @Qualifier("sseHistoryAppendScript")
            RedisScript<List> appendScript,
            @Qualifier("sseHistorySnapshotScript")
            RedisScript<List> snapshotScript,
            @Qualifier("sseHistoryReadAfterScript")
            RedisScript<List> readAfterScript) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.historyProperties = historyProperties;
        this.appendScript = appendScript;
        this.snapshotScript = snapshotScript;
        this.readAfterScript = readAfterScript;
    }

    @Override
    public SseHistoryEntry append(SseEvent<JsonNode> event) {
        // JSON 객체의 필드 순서 차이가 동일 이벤트의 내용 충돌로 오인되지 않도록 정렬한다.
        String eventJson = objectMapper.writer()
                .with(JsonNodeFeature.WRITE_PROPERTIES_SORTED)
                .writeValueAsString(event);

        List<?> rawResult = redisTemplate.execute(
                appendScript,
                getHistoryKeys(event.receiverId()),
                event.eventId().toString(),
                eventJson,
                historyProperties.retentionMillisString(),
                Integer.toString(historyProperties.maxEventsPerUser())
        );
        AppendScriptResult result = AppendScriptResult.parse(rawResult);
        if (AppendStatus.isConflict(result.status)){
            throw new SseHistoryConflictException(event.receiverId(), event.eventId());
        }

        SseEvent<JsonNode> storedEvent = objectMapper.readValue(result.eventJson, new TypeReference<>() {
        });
        return new SseHistoryEntry(new SseHistoryPosition(result.position), storedEvent);
    }

    @Override
    public SseHistorySnapshot snapshot(UUID receiverId, UUID lastEventId) {
        List<?> rawResult = redisTemplate.execute(
                snapshotScript,
                getHistoryKeys(receiverId),
                lastEventId == null ? "" : lastEventId.toString(),
                historyProperties.retentionMillisString()
        );

        SnapshotScriptResult result = SnapshotScriptResult.parse(rawResult);
        SseHistoryPosition upperBound = toSseHistoryPosition(result);
        List<SseHistoryEntry> entries = toSseHistoryEntries(result.entries);

        return new SseHistorySnapshot(upperBound, entries);
    }

    @Override
    public SseHistoryReadResult readAfter(UUID receiverId, SseHistoryPosition afterPosition) {
        List<?> rawResult = redisTemplate.execute(
                readAfterScript,
                getHistoryKeys(receiverId),
                afterPosition == null ? "" : afterPosition.value(),
                historyProperties.retentionMillisString()
        );

        ReadAfterScriptResult result = ReadAfterScriptResult.parse(rawResult);
        List<SseHistoryEntry> entries = toSseHistoryEntries(result.entries);
        return new SseHistoryReadResult(result.gapDetected, entries);
    }

    private String streamKey(UUID receiverId){
        return "%s:{%s}:stream".formatted(
                historyProperties.keyPrefix(),
                receiverId
        );
    }

    private String eventIdMappingKey(UUID receiverId){
        return "%s:{%s}:event-ids".formatted(
                historyProperties.keyPrefix(),
                receiverId
        );
    }

    private record AppendScriptResult(
            AppendStatus status,
            String position,
            String eventJson
    ){

        private static AppendScriptResult parse(List<?> result){
            if (result == null || result.size() != 3){
                throw new IllegalStateException("SSE 이력 저장 스크립트의 반환값은 3개여야 합니다.");
            }
            if (!(result.get(0) instanceof String status)
                    || !(result.get(1) instanceof String position)
                    || !(result.get(2) instanceof String storedEventJson)
            ){
                throw new IllegalStateException("SSE 이력 저장 스크립트의 반환값은 문자열이어야 합니다.");
            }

            if (!AppendStatus.isValid(status)){
                throw new IllegalStateException("SSE 이력 저장 스크립트가 알 수 없는 처리 상태를 반환했습니다.");
            }
            return new AppendScriptResult(
                    AppendStatus.valueOf(status),
                    position,
                    storedEventJson
            );
        }

    }
    private record SnapshotScriptResult(
            String upperBound,
            List<HistoryScriptEntry> entries
    ){

        private static SnapshotScriptResult parse(List<?> result){
            if (result == null || result.size() != 2){
                throw new IllegalStateException("SSE 이력 스냅샷 스크립트의 반환값은 2개여야 합니다.");
            }
            if (!(result.get(0) instanceof String upperBound)
                    || !(result.get(1) instanceof List<?> rawEntries)
            ){
                throw new IllegalStateException("SSE 이력 스냅샷 스크립트의 반환값은 마지막 위치 문자열과 이벤트 목록이어야 합니다.");
            }

            List<HistoryScriptEntry> entries = rawEntries.stream()
                    .map(HistoryScriptEntry::parse)
                    .toList();
            return new SnapshotScriptResult(upperBound, entries);
        }

    }
    private record ReadAfterScriptResult(
            boolean gapDetected,
            List<HistoryScriptEntry> entries
    ){

        private static ReadAfterScriptResult parse(List<?> result){
            if (result == null || result.size() != 2){
                throw new IllegalStateException("SSE 후속 조회 스크립트의 반환값은 2개여야 합니다.");
            }
            if (!(result.get(0) instanceof Long gapFlag)
                || !(result.get(1) instanceof List<?> rawEntries)){
                throw new IllegalStateException("SSE 후속 조회 스크립트의 반환값은 누락 상태 숫자와 이벤트 목록이어야 합니다.");
            }
            if (gapFlag != 0L && gapFlag != 1L){
                throw new IllegalStateException("SSE 후속 조회의 누락 상태는 0 또는 1이어야 합니다.");
            }
            List<HistoryScriptEntry> entries = rawEntries.stream()
                    .map(HistoryScriptEntry::parse)
                    .toList();

            return new ReadAfterScriptResult(gapFlag == 1L, entries);
        }
    }
    private record HistoryScriptEntry(
            String position,
            String eventJson
    ){

        private static HistoryScriptEntry parse(Object rawEntry){
            if (!(rawEntry instanceof List<?> entry) || entry.size() != 2){
                throw new IllegalStateException("SSE 이력 항목은 저장 위치와 이벤트 JSON으로 구성된 2개 요소의 목록이어야 합니다.");
            }
            if (!(entry.get(0) instanceof  String position)
                    || !(entry.get(1) instanceof String eventJson)){
                throw new IllegalStateException("SSE 이력 항목의 저장 위치와 이벤트 JSON은 문자열이어야 합니다.");
            }

            return new HistoryScriptEntry(position, eventJson);
        }
    }
    private enum AppendStatus{
        CONFLICT, CREATED, EXISTING;

        public static boolean isConflict(AppendStatus status){
            return CONFLICT.equals(status);
        }

        public static boolean isValid(String status){
            return Arrays.stream(AppendStatus.values())
                    .anyMatch(s -> s.name().equals(status));
        }

    }
    private SseHistoryPosition toSseHistoryPosition(SnapshotScriptResult result) {
        if (!StringUtils.hasText(result.upperBound)){
            return null;
        }

        return new SseHistoryPosition(result.upperBound);
    }

    private SseHistoryEntry toSseHistoryEntry(HistoryScriptEntry entry){
        SseEvent<JsonNode> event = objectMapper.readValue(entry.eventJson, new TypeReference<>() {});
        return new SseHistoryEntry(new SseHistoryPosition(entry.position), event);
    }

    private List<SseHistoryEntry> toSseHistoryEntries(List<HistoryScriptEntry> entries) {
        return entries.stream()
                .map(this::toSseHistoryEntry)
                .toList();
    }

    private List<String> getHistoryKeys(UUID receiverId) {
        return List.of(
                streamKey(receiverId),
                eventIdMappingKey(receiverId)
        );
    }
}
