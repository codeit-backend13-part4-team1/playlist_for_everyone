package com.codeit.sb13.playlist_for_everyone.global.sse.history.impl;

import com.codeit.sb13.playlist_for_everyone.global.config.SseHistoryConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SseHistoryConflictException;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.*;
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
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                SseHistoryConfig.class,
                RedisSseHistoryStore.class
        },
        properties = {
                "everyone.sse.history.retention=10m",
                "everyone.sse.history.max-events-per-user=2",
                "everyone.sse.history.key-prefix=test:sse:history"
        }
)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Redis SSE 이력 저장소 통합 테스트")
class RedisSseHistoryStoreTest {

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
        @DisplayName("이벤트를 저장하면 저장 위치·내용을 반환하고 Stream 이력과 UUID 매핑을 생성한다.")
        void returnsPositionAndEventWhenAppending(){
                UUID eventId = UUID.randomUUID();
                UUID receiverId = UUID.randomUUID();

                JsonNode data = objectMapper.valueToTree(Map.of(
                        "message","이력 저장 테스트",
                        "sender",Map.of(
                                "id", UUID.randomUUID().toString(),
                                "name", "테스트 사용자"),
                        "tags", List.of("영화", "드라마")
                ));

                SseEvent<JsonNode> event = SseEvent.createNotificationEvent(eventId, receiverId, data);

                SseHistoryEntry saved = historyStore.append(event);

                assertThat(saved).isNotNull();
                assertThat(saved.position()).isNotNull();
                assertThat(saved.position().value()).isNotBlank();
                assertThat(saved.event()).isEqualTo(event);

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);
                assertThat(records).hasSize(1);

                MapRecord<String, String, String> record = records.get(0);
                assertThat(record.getId().getValue())
                        .isEqualTo(saved.position().value());

                String storedEvent = record.getValue().get("event");
                assertThat(storedEvent).isNotBlank();

                JsonNode storedEventJson = objectMapper.readTree(storedEvent);
                JsonNode expectedEventJson = objectMapper.valueToTree(event);
                assertThat(storedEventJson).isEqualTo(expectedEventJson);

                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(eventId.toString(), saved.position().value()));
        }

        @Test
        @DisplayName("보관 한도에서 동일 이벤트를 재저장하면 기존 위치를 반환하고 이력과 UUID 매핑을 유지한다.")
        void returnsExistingEntryWhenAppendingSameEventAgain(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseEvent<JsonNode> duplicateEvent = SseEvent.createNotificationEvent(firstEvent.eventId(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseHistoryEntry duplicateSaved = historyStore.append(duplicateEvent);

                assertThat(duplicateSaved).isEqualTo(firstSaved);

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);
                assertThat(records)
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(firstSaved.position().value(), secondSaved.position().value());

                JsonNode firstEventJson = objectMapper.valueToTree(firstEvent);
                JsonNode secondEventJson = objectMapper.valueToTree(secondEvent);

                assertThat(records)
                        .extracting(record ->
                                objectMapper.readTree(record.getValue().get("event")))
                                .containsExactly(firstEventJson, secondEventJson);
                assertThat(redisTemplate.<String,String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .isEqualTo(
                                Map.of(
                                        firstEvent.eventId().toString(), firstSaved.position().value(),
                                        secondEvent.eventId().toString(), secondSaved.position().value()
                                ));
        }

        @Test
        @DisplayName("이벤트 ID가 같아도 수신자가 다르면 각각 저장하고 기존 이력을 유지한다.")
        void storesSameEventIdIndependentlyForDifferentReceivers(){
                UUID eventId = UUID.randomUUID();
                UUID firstReceiverId = UUID.randomUUID();
                UUID secondReceiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(eventId, firstReceiverId, createTestMessageJson());
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(eventId, secondReceiverId, createTestMessageJson());

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                assertThat(firstSaved.event()).isEqualTo(firstEvent);
                assertThat(secondSaved.event()).isEqualTo(secondEvent);

                assertSingleStoredEvent(firstEvent, firstSaved.position());
                assertSingleStoredEvent(secondEvent, secondSaved.position());
        }

        @Test
        @DisplayName("데이터가 null인 이벤트도 저장 결과를 반환하고 Stream 이력과 UUID 매핑을 생성한다.")
        void appendsEventWithNullData(){
                SseEvent<JsonNode> event = SseEvent.createNotificationEvent(UUID.randomUUID(), UUID.randomUUID(), null);
                SseHistoryEntry saved = historyStore.append(event);

                assertThat(saved).isNotNull();
                assertThat(saved.position()).isNotNull();
                assertThat(saved.position().value()).isNotBlank();

                JsonNode expectedEventJson = objectMapper.valueToTree(event);
                JsonNode savedEventJson = objectMapper.valueToTree(saved.event());

                assertThat(savedEventJson).isEqualTo(expectedEventJson);
                assertSingleStoredEvent(event, saved.position());
        }

        @Test
        @DisplayName("동일 수신자와 이벤트 ID에 다른 데이터를 저장하면 충돌 예외가 발생하고 기존 이력을 유지한다.")
        void rejectsDifferentDataForSameEventAndPreservesExistingEntry(){
                UUID eventId = UUID.randomUUID();
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> originalEvent = SseEvent.createNotificationEvent(eventId, receiverId, createTestMessageJson());
                SseHistoryEntry firstSaved = historyStore.append(originalEvent);
                SseEvent<JsonNode> conflictingEvent = SseEvent.createNotificationEvent(eventId, receiverId, createTestMessageJson("기존 이력 과 다른 내용"));

                assertThatThrownBy(() -> historyStore.append(conflictingEvent))
                        .isInstanceOf(SseHistoryConflictException.class);

                assertSingleStoredEvent(originalEvent, firstSaved.position());
        }

        @Test
        @DisplayName("동일 수신자와 이벤트 ID에 다른 이벤트 이름으로 저장하면 충돌 예외가 발생하고 기존 이력을 유지한다.")
        void rejectsDifferentEventNameForSameEventAndPreservesExistingEntry(){
                UUID eventId = UUID.randomUUID();
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> originalEvent = SseEvent.createNotificationEvent(eventId, receiverId, createTestMessageJson());
                SseHistoryEntry firstSaved = historyStore.append(originalEvent);

                SseEvent<JsonNode> conflictingEvent = new SseEvent<>(
                        eventId,
                        receiverId,
                        Event.DIRECT_MESSAGES.getName(),
                        createTestMessageJson()
                );

                assertThatThrownBy(()-> historyStore.append(conflictingEvent))
                        .isInstanceOf(SseHistoryConflictException.class);

                assertSingleStoredEvent(originalEvent, firstSaved.position());
        }

        @Test
        @DisplayName("동일 수신자의 서로 다른 이벤트는 UUID 문자열 순서와 관계없이 저장한 순서대로 보관한다.")
        void preservesAppendOrderForDifferentEventsOfSameReceiver(){
                UUID receiverId = UUID.randomUUID();
                UUID firstEventId = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
                UUID secondEventId = UUID.fromString("00000000-0000-4000-8000-000000000001");

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(firstEventId, receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(secondEventId, receiverId, createTestMessageJson("두 번째 이벤트"));

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);
                assertThat(records)
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(
                                firstSaved.position().value(),
                                secondSaved.position().value()
                        );

                JsonNode firstEventJson = objectMapper.valueToTree(firstEvent);
                JsonNode secondEventJson = objectMapper.valueToTree(secondEvent);

                assertThat(records)
                        .extracting(record ->
                                objectMapper.readTree(record.getValue().get("event")))
                        .containsExactly(firstEventJson, secondEventJson);

                assertThat(redisTemplate.<String,String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .isEqualTo(Map.of(
                                firstEventId.toString(), firstSaved.position().value(),
                                secondEventId.toString(), secondSaved.position().value()
                        ));
        }

        @Test
        @DisplayName("동일 이벤트를 동시에 저장하면 같은 저장 위치를 반환하고 이력과 UUID 매핑은 한 건만 생성한다.")
        void storesSingleEntryWhenAppendingSameEventConcurrently() throws ExecutionException, InterruptedException, TimeoutException {
                SseEvent<JsonNode> event = SseEvent.createNotificationEvent(UUID.randomUUID(), UUID.randomUUID(), createTestMessageJson());

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CyclicBarrier barrier = new CyclicBarrier(2);

                Callable<SseHistoryEntry> appendRequest = () ->{
                  barrier.await(10, TimeUnit.SECONDS);
                  return historyStore.append(event);
                };

                try {
                        Future<SseHistoryEntry> firstFuture = executor.submit(appendRequest);
                        Future<SseHistoryEntry> secondFuture = executor.submit(appendRequest);

                        SseHistoryEntry firstSaved = firstFuture.get(20, TimeUnit.SECONDS);
                        SseHistoryEntry secondSaved = secondFuture.get(20, TimeUnit.SECONDS);

                        assertThat(firstSaved.event()).isEqualTo(event);
                        assertThat(secondSaved.event()).isEqualTo(event);
                        assertThat(secondSaved.position()).isEqualTo(firstSaved.position());
                        assertSingleStoredEvent(event, firstSaved.position());
                } finally {
                        executor.shutdownNow();
                        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                                .as("테스트 작업 스레드가 종료되어야 한다.")
                                .isTrue();
                }
        }

        @Test
        @DisplayName("최대 보관 개수를 초과하면 가장 오래된 이력과 UUID 매핑을 제거하고 최신 이력을 유지한다.")
        void removesOldestEntryAndMappingWhenExceedingMaxEvents(){
                UUID receiverId = UUID.randomUUID();
                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));
                SseEvent<JsonNode> thirdEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("세 번째 이벤트"));

                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);
                SseHistoryEntry thirdSaved = historyStore.append(thirdEvent);

                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);
                assertThat(records)
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(
                                secondSaved.position().value(),
                                thirdSaved.position().value()
                        );

                JsonNode secondEventJson = objectMapper.valueToTree(secondEvent);
                JsonNode thirdEventJson = objectMapper.valueToTree(thirdEvent);
                assertThat(records)
                        .extracting(record ->
                                objectMapper.readTree(record.getValue().get("event")))
                        .containsExactly(secondEventJson, thirdEventJson);
                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .isEqualTo(
                                Map.of(
                                        secondEvent.eventId().toString(), secondSaved.position().value(),
                                        thirdEvent.eventId().toString(), thirdSaved.position().value()
                                ));
        }

        @Test
        @DisplayName("이벤트를 저장하면 Stream과 UUID 매핑 양쪽에 만료시간을 설정한다.")
        void setsTtlOnStreamAndEventIdMappingWhenAppending(){
                UUID receiverId = UUID.randomUUID();
                SseEvent<JsonNode> event = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson());

                historyStore.append(event);
                long retentionMillis = historyProperties.retention().toMillis();

                Long streamTtl = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtl = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);

                assertThat(streamTtl)
                        .as("Stream 이력의 남은 만료시간")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(retentionMillis);

                assertThat(mappingTtl)
                        .as("UUID 매핑의 남은 만료시간")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(retentionMillis);
        }

        @Test
        @DisplayName("새로운 이벤트를 저장하면 Stream과 UUID 매핑의 TTL을 갱신한다.")
        void refreshesTtlWhenAppendingNewEvent() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(firstEvent);
                TimeUnit.SECONDS.sleep(1);

                long retentionMillis = historyProperties.retention().toMillis();
                Long streamTtlBefore = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlBefore = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);
                assertThat(streamTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);
                assertThat(mappingTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);

                historyStore.append(secondEvent);
                Long streamTtlAfter = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlAfter = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);

                assertThat(streamTtlAfter)
                        .as("새 이벤트 저장 시 Stream의 TTL을 갱신해야 한다.")
                        .isNotNull()
                        .isGreaterThan(streamTtlBefore)
                        .isLessThanOrEqualTo(retentionMillis);
                assertThat(mappingTtlAfter)
                        .as("새 이벤트 저장 시 UUID 매핑의 TTL을 갱신해야 한다.")
                        .isNotNull()
                        .isGreaterThan(mappingTtlBefore)
                        .isLessThanOrEqualTo(retentionMillis);
        }

        @Test
        @DisplayName("동일 이벤트를 재저장해도 Stream과 UUID 매핑의 TTL을 연장하지 않는다.")
        void doesNotRefreshTtlWhenAppendingSameEventAgain() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                SseEvent<JsonNode> event = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson());

                historyStore.append(event);

                TimeUnit.SECONDS.sleep(1);

                long retentionMillis = historyProperties.retention().toMillis();
                Long streamTtlBefore = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlBefore = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);
                assertThat(streamTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);
                assertThat(mappingTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);

                historyStore.append(event);
                Long streamTtlAfter = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlAfter = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);

                assertThat(streamTtlAfter)
                        .as("중복 저장으로 Stream의 TTL이 연장되면 안 된다.")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(streamTtlBefore);
                assertThat(mappingTtlAfter)
                        .as("중복 저장으로 UUID 매핑의 TTL이 연장되면 안 된다.")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(mappingTtlBefore);
        }

        @Test
        @DisplayName("저장된 이력이 없으면 마지막 저장 위치 없이 빈 스냅샷을 반환한다.")
        void returnsEmptySnapshotWhenHistoryDoesNotExist(){
                UUID receiverId = UUID.randomUUID();
                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, null);

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isNull();
                assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("이력이 있는 사용자가 마지막 수신 이벤트 ID없이 조회하면 현재 마지막 위치와 빈 복구목록을 반환한다.")
        void returnsCurrentTailWithoutReplayWhenLastEventIdIsMissing(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);
                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, null);

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(secondSaved.position());
                assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("유효한 마지막 수신 이벤트 ID로 조회하면 해당 이벤트 이후의 이력만 반환한다.")
        void returnsEntriesAfterLastEventIdWhenCursorIsValid(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, firstEvent.eventId());
                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(secondSaved.position());
                assertThat(snapshot.entries()).containsExactly(secondSaved);
        }

        @Test
        @DisplayName("마지막 저장 이벤트까지 수신했다면 현재 마지막 위치와 빈 복구 목록을 반환한다.")
        void returnsEmptyReplayWhenLastEventIdIsCurrentTail(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, secondEvent.eventId());

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(secondSaved.position());
                assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 마지막 수신 이벤트 ID로 조회하면 현재 마지막 위치와 빈 복구 목록을 반환한다.")
        void returnsCurrentTailWithoutReplayWhenLastEventIdIsUnknown(){
                UUID receiverId = UUID.randomUUID();
                UUID unknownEventId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, unknownEventId);

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(secondSaved.position());
                assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 이벤트 ID로 조회하면 본인의 마지막 위치와 빈 복구 목록을 반환한다.")
        void returnsCurrentTailWithoutReplayWhenLastEventIdBelongsToAnotherReceiver(){
                UUID receiverId = UUID.randomUUID();
                UUID otherReceiverId = UUID.randomUUID();

                SseEvent<JsonNode> otherEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), otherReceiverId, createTestMessageJson("다른 사용자 이벤트"));
                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(otherEvent);
                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, otherEvent.eventId());

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(secondSaved.position());
                assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("보관 개수 제한으로 제거된 이벤트 ID로 조회하면 현재 마지막 위치와 빈 복구 목록을 반환한다.")
        void returnsCurrentTailWithoutReplayWhenLastEventIdWasTrimmed(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));
                SseEvent<JsonNode> thirdEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("세 번째 이벤트"));

                historyStore.append(firstEvent);
                historyStore.append(secondEvent);
                SseHistoryEntry thirdSaved = historyStore.append(thirdEvent);

                SseHistorySnapshot snapshot = historyStore.snapshot(receiverId, firstEvent.eventId());

                assertThat(snapshot).isNotNull();
                assertThat(snapshot.upperBound()).isEqualTo(thirdSaved.position());
                assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("스냅샷을 조회해도 Stream과 UUID 매핑의 TTL을 연장하지 않는다.")
        void doesNotRefreshTtlWhenTakingSnapshot() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();
                SseEvent<JsonNode> event = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson());

                historyStore.append(event);

                TimeUnit.SECONDS.sleep(1);

                long retentionMillis = historyProperties.retention().toMillis();
                Long streamTtlBefore = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlBefore = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);
                assertThat(streamTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);
                assertThat(mappingTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);

                historyStore.snapshot(receiverId, event.eventId());
                Long streamTtlAfter = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlAfter = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);

                assertThat(streamTtlAfter)
                        .as("스냅샷 조회로 Stream의 TTL이 연장되면 안 된다.")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(streamTtlBefore);
                assertThat(mappingTtlAfter)
                        .as("스냅샷 조회로 UUID 매핑의 TTL이 연장되면 안 된다.")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(mappingTtlBefore);
        }

        @Test
        @DisplayName("저장된 이력과 기준 위치가 없으면 누락 감지 없이 빈 후속 조회 결과를 반환한다.")
        void returnsEmptyReadResultWhenHistoryAndPositionAreAbsent(){
                UUID receiverId = UUID.randomUUID();
                SseHistoryReadResult result = historyStore.readAfter(receiverId, null);

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).isEmpty();
        }

        @Test
        @DisplayName("기준 위치가 없으면 저장된 이력을 저장 순서대로 반환한다.")
        void returnsEntriesInAppendOrderWhenAfterPositionIsNull(){
                UUID receiverId = UUID.randomUUID();
                UUID firstEventId = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
                UUID secondEventId = UUID.fromString("00000000-0000-4000-8000-000000000001");

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(firstEventId, receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(secondEventId, receiverId, createTestMessageJson("두 번째 이벤트"));

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, null);
                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).containsExactly(firstSaved, secondSaved);
        }

        @Test
        @DisplayName("유효한 기준 위치로 후속 조회하면 해당 위치 이후의 이력만 반환한다.")
        void returnsEntriesAfterGivenPositionWithoutGap(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, firstSaved.position());

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).containsExactly(secondSaved);
        }

        @Test
        @DisplayName("마지막 저장 위치 이후에 새 이벤트가 없으면 누락 감지 없이 빈 결과를 반환한다.")
        void returnsEmptyReadResultWithoutGapWhenPositionIsCurrentTail(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                historyStore.append(firstEvent);
                SseHistoryEntry secondSaved = historyStore.append(secondEvent);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, secondSaved.position());

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isFalse();
                assertThat(result.entries()).isEmpty();
        }

        @Test
        @DisplayName("기준 위치 이후의 미수신 이력이 보관개수 제한으로 제거되면 누락을 감지하고 빈 목록을 반환한다.")
        void detectsGapWhenUnreadEntriesAreTrimmed(){
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));
                SseEvent<JsonNode> thirdEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("세 번째 이벤트"));
                SseEvent<JsonNode> fourthEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("네 번째 이벤트"));

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                historyStore.append(secondEvent);
                historyStore.append(thirdEvent);
                historyStore.append(fourthEvent);

                SseHistoryReadResult result = historyStore.readAfter(receiverId, firstSaved.position());

                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isTrue();
                assertThat(result.entries()).isEmpty();
        }

        @Test
        @DisplayName("후속 이력을 조회해도 Stream과 UUID매핑의 TTL을 연장하지 않는다.")
        void doesNotRefreshTtlWhenReadingAfterPosition() throws InterruptedException {
                UUID receiverId = UUID.randomUUID();

                SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("첫 번째 이벤트"));
                SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, createTestMessageJson("두 번째 이벤트"));

                SseHistoryEntry firstSaved = historyStore.append(firstEvent);
                historyStore.append(secondEvent);

                TimeUnit.SECONDS.sleep(1);

                long retentionMillis = historyProperties.retention().toMillis();

                Long streamTtlBefore = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlBefore = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);
                assertThat(streamTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);

                assertThat(mappingTtlBefore)
                        .isNotNull()
                        .isPositive()
                        .isLessThan(retentionMillis);

                historyStore.readAfter(receiverId, firstSaved.position());

                Long streamTtlAfter = redisTemplate.getExpire(streamKey(receiverId), TimeUnit.MILLISECONDS);
                Long mappingTtlAfter = redisTemplate.getExpire(eventIdMappingKey(receiverId), TimeUnit.MILLISECONDS);

                assertThat(streamTtlAfter)
                        .as("후속 조회로 Stream의 TTL이 연장되면 안 된다.")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(streamTtlBefore);

                assertThat(mappingTtlAfter)
                        .as("후속 조회로 UUID 매핑의 TTL이 연장되면 안 된다.")
                        .isNotNull()
                        .isPositive()
                        .isLessThanOrEqualTo(mappingTtlBefore);
        }

        private JsonNode createTestMessageJson() {
                return createTestMessageJson("이력 저장 테스트");
        }

        private JsonNode createTestMessageJson(String message) {
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

        private List<MapRecord<String, String, String>> findAllEventsByReceiverId(UUID receiverId) {
                return redisTemplate.<String, String>opsForStream()
                        .range(streamKey(receiverId), Range.unbounded());
        }

        private void assertSingleStoredEvent(
                SseEvent<JsonNode> expectedEvent,
                SseHistoryPosition expectedPosition
        ){
                UUID receiverId = expectedEvent.receiverId();
                List<MapRecord<String, String, String>> records = findAllEventsByReceiverId(receiverId);

                assertThat(records)
                        .extracting(record -> record.getId().getValue())
                        .containsExactly(expectedPosition.value());

                JsonNode expectedEventJson = objectMapper.valueToTree(expectedEvent);
                assertThat(records)
                        .extracting(record -> objectMapper.readTree(record.getValue().get("event")))
                        .containsExactly(expectedEventJson);
                assertThat(redisTemplate.<String, String>opsForHash()
                        .entries(eventIdMappingKey(receiverId)))
                        .containsExactlyEntriesOf(Map.of(expectedEvent.eventId().toString(),
                                expectedPosition.value()));
        }
}