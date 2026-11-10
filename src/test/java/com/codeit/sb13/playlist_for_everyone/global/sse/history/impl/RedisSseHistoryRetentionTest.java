package com.codeit.sb13.playlist_for_everyone.global.sse.history.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.SseHistoryConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryReadResult;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistorySnapshot;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                SseHistoryConfig.class,
                RedisSseHistoryStore.class
        },
        properties = {
                "everyone.sse.history.retention=10s",
                "everyone.sse.history.max-events-per-user=100",
                "everyone.sse.history.key-prefix=test:sse:history:retention"
        }
)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Redis SSE 이력 보관기간 및 만료 통합 테스트")
public class RedisSseHistoryRetentionTest {

        @Container
        static final GenericContainer<?> redis = new GenericContainer<>(
                DockerImageName.parse("redis:7.4-alpine")
        ).withExposedPorts(6379);

        @DynamicPropertySource
        static void configureRedis(DynamicPropertyRegistry registry){
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }

        @Autowired
        private SseHistoryStore historyStore;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private StringRedisTemplate redisTemplate;

        @Autowired
        private SseHistoryProperties historyProperties;


        @Test
        @DisplayName("새 이벤트로 키의 TTL이 갱신되어도 보관기간이 지난 이력과 UUID 매핑은 제거하고 최근이력은 유지한다.")
        void removesExpiredEntriesAndMappingsWhenAppendingAfterTtlRefresh() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                long retentionMillis = historyProperties.retention().toMillis();
                long halfRetentionMills = retentionMillis / 2;

                SseEvent<JsonNode> firstEvent = createTestSseEvent(receiverId, "첫 번째 이벤트");
                SseEvent<JsonNode> secondEvent = createTestSseEvent(receiverId, "두 번째 이벤트");
                SseEvent<JsonNode> thirdEvent = createTestSseEvent(receiverId, "세 번째 이벤트");

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                TimeUnit.MILLISECONDS.sleep(halfRetentionMills);

                SseHistoryEntry secondSaved = historyStore.append(secondEvent);
                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(firstSaved.position().value(), secondSaved.position().value());

                TimeUnit.MILLISECONDS.sleep(retentionMillis - halfRetentionMills + 200);

                SseHistoryEntry thirdSaved = historyStore.append(thirdEvent);

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);

                assertThat(records)
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(secondSaved.position().value(),
                                thirdSaved.position().value());

                JsonNode secondEventJson = objectMapper.valueToTree(secondEvent);
                JsonNode thirdEventJson = objectMapper.valueToTree(thirdEvent);

                assertThat(records)
                        .extracting(record ->
                                objectMapper.readTree(record.getValue().get("event")))
                        .containsExactly(secondEventJson, thirdEventJson);

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .isEqualTo(Map.of(
                                secondEvent.eventId().toString(), secondSaved.position().value(),
                                thirdEvent.eventId().toString(), thirdSaved.position().value()
                        ));
        }

        @Test
        @DisplayName("만료된 이벤트 ID로 스냅샷을 조회하면 오래된 이력과 매핑을 정리하고 현재 마지막 위치와 빈 복구 목록을 반환한다.")
        void removesExpiredEntriesAndMappingsWhenTakingSnapshotWithExpiredCursor() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                long retentionMillis = historyProperties.retention().toMillis();
                long halfRetentionMillis = retentionMillis / 2;

                SseEvent<JsonNode> firstEvent = createTestSseEvent(receiverId, "첫 번째 이벤트");
                SseEvent<JsonNode> secondEvent = createTestSseEvent(receiverId, "두 번째 이벤트");
                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                TimeUnit.MILLISECONDS.sleep(halfRetentionMillis);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(
                                firstSaved.position().value(),
                                secondSaved.position().value());

                TimeUnit.MILLISECONDS.sleep(retentionMillis - halfRetentionMillis + 200);

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, firstEvent.eventId());
                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(secondSaved.position());
                assertThat(snapshot.entries()).isEmpty();

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);

                assertThat(records)
                        .extracting(record ->record.getId().getValue())
                        .containsExactly(secondSaved.position().value());

                JsonNode secondEventJson = objectMapper.valueToTree(secondEvent);
                assertThat(records)
                        .extracting(record ->
                                objectMapper.readTree(record.getValue().get("event")))
                        .containsExactly(secondEventJson);

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(
                                secondEvent.eventId().toString(), secondSaved.position().value()
                        ));
        }

        @Test
        @DisplayName("기준 이력이 만료되어도 남은 후속이력을 반환하고 만료 이력과 UUID 매핑을 정리한다.")
        void returnsRemainingEntriesAndRemovesExpiredHistoryWhenAfterPositionExpires() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                long retentionMillis = historyProperties.retention().toMillis();
                long halfRetentionMillis = retentionMillis / 2;

                SseEvent<JsonNode> firstEvent = createTestSseEvent(receiverId, "이미 수신한 이벤트");
                SseEvent<JsonNode> secondEvent = createTestSseEvent(receiverId, "미수신 상태로 만료될 이벤트");
                SseEvent<JsonNode> thirdEvent = createTestSseEvent(receiverId, "아직 유효한 이벤트");

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                TimeUnit.MILLISECONDS.sleep(halfRetentionMillis);

                SseHistoryEntry thirdSaved = historyStore.append(thirdEvent);

                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(
                                firstSaved.position().value(),
                                secondSaved.position().value(),
                                thirdSaved.position().value());

                TimeUnit.MILLISECONDS.sleep(retentionMillis - halfRetentionMillis + 200);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, firstSaved.position());

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isTrue();
                assertThat(result.entries()).containsExactly(thirdSaved);

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);

                assertThat(records)
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(thirdSaved.position().value());

                JsonNode thirdEventJson = objectMapper.valueToTree(thirdEvent);

                assertThat(records)
                        .extracting(record ->
                                objectMapper.readTree(record.getValue().get("event")))
                        .containsExactly(thirdEventJson);

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(
                                thirdEvent.eventId().toString(),
                                thirdSaved.position().value()
                        ));
        }

        @Test
        @DisplayName("전체 이력이 만료되면 이전 이벤트 ID로 빈 스냅샷을 반환하고 이력 키를 다시 생성하지 않는다.")
        void returnsEmptySnapshotWithoutRecreatingKeysWhenEntireHistoryExpires(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> event = createTestSseEvent(receiverId, "만료 확인 이벤트");

                historyStore.append(event);

                String streamKey = streamKey(receiverId);
                String mappingKey = eventIdMappingKey(receiverId);

                assertThat(redisTemplate.hasKey(streamKey)).isTrue();
                assertThat(redisTemplate.hasKey(mappingKey)).isTrue();

                await()
                        .atMost(historyProperties.retention().plusSeconds(3))
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(()->{
                                assertThat(redisTemplate.hasKey(streamKey))
                                        .as("보관기간이 지나면 Stream 키가 만료되어야 한다.")
                                        .isFalse();

                                assertThat(redisTemplate.hasKey(mappingKey))
                                        .as("보관기간이 지나면 UUID 매핑 키가 만료되어야한다.")
                                        .isFalse();
                        });

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, event.eventId());

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isNull();
                assertThat(snapshot.entries()).isEmpty();
                assertThat(redisTemplate.hasKey(streamKey)).isFalse();
                assertThat(redisTemplate.hasKey(mappingKey)).isFalse();
        }

        @Test
        @DisplayName("동일 이벤트를 재저장해도 최초 저장시점의 보관기간이 지나면 이력과 UUID 매핑을 제거한다.")
        void doesNotExtendOriginalRetentionWhenAppendingSameEventAgain() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                long retentionMillis = historyProperties.retention().toMillis();
                long halfRetentionMillis = retentionMillis / 2;

                SseEvent<JsonNode> firstEvent = createTestSseEvent(receiverId, "중복 저장할 이벤트");
                SseEvent<JsonNode> secondEvent = createTestSseEvent(receiverId, "최근에 저장한 이벤트");

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);

                TimeUnit.MILLISECONDS.sleep(halfRetentionMillis);

                SseHistoryEntry secondSaved = historyStore.append(secondEvent);
                SseHistoryEntry duplicateSaved = historyStore.append(firstEvent);

                assertThat(duplicateSaved).isEqualTo(firstSaved);

                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(firstSaved.position().value(),
                                secondSaved.position().value());

                TimeUnit.MILLISECONDS.sleep(retentionMillis - halfRetentionMillis + 200);

                historyStore.snapshot(receiverId, null);

                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(secondSaved.position().value());

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(
                                secondEvent.eventId().toString(),
                                secondSaved.position().value()
                        ));
        }

        @Test
        @DisplayName("미수신 이벤트를 포함한 전체 이력이 만료되면 기존 위치의 후속 조회에서 누락을 감지한다.")
        void detectsGapWhenEntireHistoryIncludingUnreadEventsExpires(){
                UUID receiverId = UUID.randomUUID();
                SseEvent<JsonNode> firstEvent = createTestSseEvent(receiverId, "이미 수신한 이벤트");
                SseEvent<JsonNode> secondEvent = createTestSseEvent(receiverId, "아직 수신하지 않은 이벤트");

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                historyStore.append(secondEvent);

                String streamKey = streamKey(receiverId);
                String mappingKey = eventIdMappingKey(receiverId);

                assertThat(redisTemplate.hasKey(streamKey)).isTrue();
                assertThat(redisTemplate.hasKey(mappingKey)).isTrue();

                await()
                        .atMost(historyProperties.retention().plusSeconds(3))
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() ->{
                                assertThat(redisTemplate.hasKey(streamKey)).isFalse();
                                assertThat(redisTemplate.hasKey(mappingKey)).isFalse();
                        });

                SseHistoryReadResult result = historyStore.readAfter(receiverId, firstSaved.position());

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isTrue();
                assertThat(result.entries()).isEmpty();

                assertThat(redisTemplate.hasKey(streamKey)).isFalse();
                assertThat(redisTemplate.hasKey(mappingKey)).isFalse();
        }

        @Test
        @DisplayName("스냅샷으로 조회한 이벤트도 최초보관기간이 지나면 이력과 UUID 매핑을 제거한다.")
        void doesNotExtendOriginalRetentionWhenTakingSnapshot() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                long retentionMillis = historyProperties.retention().toMillis();
                long halfRetentionMillis = retentionMillis / 2;

                SseEvent<JsonNode> cursorEvent = createTestSseEvent(receiverId, "복구 기준 이벤트");
                SseEvent<JsonNode> targetEvent = createTestSseEvent(receiverId, "조회 후 만료될 이벤트");
                SseEvent<JsonNode> recentEvent = createTestSseEvent(receiverId, "최근 이벤트");

                historyStore.append(cursorEvent);
                SseHistoryEntry targetSaved = historyStore.append(targetEvent);
                TimeUnit.MILLISECONDS.sleep(halfRetentionMillis);

                SseHistoryEntry recentSaved = historyStore.append(recentEvent);
                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, cursorEvent.eventId());

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.entries()).containsExactly(targetSaved, recentSaved);

                TimeUnit.MILLISECONDS.sleep(retentionMillis - halfRetentionMillis + 200);

                historyStore.snapshot(receiverId, null);
                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(recentSaved.position().value());

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(
                                recentEvent.eventId().toString(),
                                recentSaved.position().value()
                        ));
        }

        @Test
        @DisplayName("후속 조회로 읽은 이벤트도 최초 보관기간이 지나면 이력과 UUID 매핑을 제거한다.")
        void doesNotExtendOriginalRetentionWhenReadingAfterPosition() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                long retentionMillis = historyProperties.retention().toMillis();
                long halfRetentionMillis = retentionMillis / 2;

                SseEvent<JsonNode> cursorEvent = createTestSseEvent(receiverId, "복구 기준 이벤트");
                SseEvent<JsonNode> targetEvent = createTestSseEvent(receiverId, "조회 후 만료될 이벤트");
                SseEvent<JsonNode> recentEvent = createTestSseEvent(receiverId, "최근 이벤트");

                SseHistoryEntry cursorSaved = historyStore.append(cursorEvent);
                SseHistoryEntry targetSaved = historyStore.append(targetEvent);

                TimeUnit.MILLISECONDS.sleep(halfRetentionMillis);

                SseHistoryEntry recentSaved = historyStore.append(recentEvent);
                SseHistoryReadResult result = historyStore.readAfter(receiverId, cursorSaved.position());

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).containsExactly(targetSaved, recentSaved);

                TimeUnit.MILLISECONDS.sleep(retentionMillis - halfRetentionMillis + 200);

                historyStore.snapshot(receiverId, null);
                assertThat(findAllEventsByReceiverId(receiverId))
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(recentSaved.position().value());

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(
                                recentEvent.eventId().toString(),
                                recentSaved.position().value()
                        ));
        }

        private SseEvent<JsonNode> createTestSseEvent(UUID receiverId, String message) {
                return SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson(message));
        }

        private JsonNode createTestMessageJson(String message){
                return objectMapper.valueToTree(Map.of("message", message));
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

        private List<MapRecord<String, String, String>> findAllEventsByReceiverId(UUID receiverId){
                return redisTemplate.<String, String>opsForStream()
                        .range(streamKey(receiverId), Range.unbounded());
        }

}
