package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM 메시지 읽음 처리 서비스 통합 테스트")
public class DirectMessageReadServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ConversationService conversationService;

    @MockitoBean
    private FileStorage fileStorage;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID counterpartId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private final UUID conversationId = UUID.randomUUID();
    private final UUID otherConversationId = UUID.randomUUID();

    private final LocalDateTime baseCreatedAt = LocalDateTime.of(2026, 11, 14,
            10, 0, 0, 123_456_000);

    @BeforeEach
    void setUp() {
        insertUser(requesterId, "요청자");
        insertUser(counterpartId, "대화 상대");
        insertUser(otherUserId, "다른 대화 상대");

        insertConversation(
                conversationId,
                requesterId,
                counterpartId,
                baseCreatedAt.minusDays(1)
        );

        insertConversation(
                otherConversationId,
                requesterId,
                otherUserId,
                baseCreatedAt.minusDays(1)
        );
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
              DELETE FROM direct_messages
              WHERE conversation_id IN (?, ?)
              """, conversationId, otherConversationId);

        jdbcTemplate.update("""
              DELETE FROM conversations
              WHERE id IN (?, ?)
              """, conversationId, otherConversationId);

        jdbcTemplate.update("""
              DELETE FROM users
              WHERE id IN (?, ?, ?)
              """, requesterId, counterpartId, otherUserId);
    }

    @Test
    @DisplayName("기준 메시지까지 해당 대화방에서 요청자가 받은 미읽음 메시지만 읽음 처리한다.")
    void read_markOnlyUnreadReceivedMessagesUpToReference(){
        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID referenceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID greaterId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID newerId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID sentId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID alreadyReadId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID otherConversationMessageId = UUID.fromString("00000000-0000-0000-0000-000000000006");

        insertMessage(
                olderId, conversationId, counterpartId, requesterId,
                "이전 수신 메시지", false,
                baseCreatedAt.minusNanos(1_000),
                null
        );
        insertMessage(
                smallerId, conversationId, counterpartId, requesterId,
                "같은 시각의 작은 ID", false,
                baseCreatedAt, baseCreatedAt
        );
        insertMessage(
                referenceId, conversationId, counterpartId, requesterId,
                "기준 메시지", false,
                baseCreatedAt, baseCreatedAt
        );
        insertMessage(
                greaterId, conversationId, counterpartId, requesterId,
                "같은 시각의 큰 ID", false,
                baseCreatedAt, baseCreatedAt
        );
        insertMessage(
                newerId, conversationId, counterpartId, requesterId,
                "이후 수신 메시지", false,
                baseCreatedAt.plusNanos(1_000),
                baseCreatedAt.plusNanos(1_000)
        );
        insertMessage(
                sentId, conversationId, requesterId, counterpartId,
                "요청자가 보낸 메시지", false,
                baseCreatedAt.minusMinutes(1),
                baseCreatedAt.minusMinutes(1)
        );
        insertMessage(
                alreadyReadId, conversationId, counterpartId, requesterId,
                "이미 읽은 메시지", true,
                baseCreatedAt.minusMinutes(1),
                baseCreatedAt.minusSeconds(10)
        );
        insertMessage(
                otherConversationMessageId, otherConversationId, otherUserId, requesterId,
                "다른 대화방 수신 메시지", false,
                baseCreatedAt.minusMinutes(1),
                baseCreatedAt.minusMinutes(1)
        );

        Map<UUID, MessageSnapshot> before = findMessages();

        directMessageService.read(new DirectMessageReadCommand(
                conversationId,
                referenceId,
                requesterId
        ));

        List<UUID> readMessageIds = jdbcTemplate.queryForList("""
                    SELECT id
                    FROM direct_messages
                    WHERE conversation_id IN (?, ?)
                        AND is_read = true
                """, UUID.class, conversationId, otherConversationId);

        assertThat(readMessageIds).containsExactlyInAnyOrder(
                olderId,
                smallerId,
                referenceId,
                alreadyReadId);

        Map<UUID, MessageSnapshot> after = findMessages();
        assertThat(after.keySet())
                .containsExactlyInAnyOrderElementsOf(before.keySet());

        Set<UUID> changedIds = Set.of(olderId, smallerId, referenceId);

        LocalDateTime updatedAt = after.get(referenceId).updatedAt;
        assertThat(updatedAt).isNotNull();

        before.forEach((messageId, previous) ->{
            MessageSnapshot actual = after.get(messageId);
            if (changedIds.contains(messageId)){
                assertThat(actual.updatedAt)
                        .isEqualTo(updatedAt)
                        .isNotEqualTo(previous.updatedAt);
                assertThat(actual)
                        .usingRecursiveComparison()
                        .ignoringFields("read", "updatedAt")
                        .isEqualTo(previous);
                return;
            }

            assertThat(actual).isEqualTo(previous);
        });
    }

    @Test
    @DisplayName("요청자가 보낸 메시지를 기준으로 이전 수신 메시지만 읽음 처리한다.")
    void read_acceptsSentMessageAsReference(){
        UUID receivedId = UUID.randomUUID();
        UUID sentReferenceId = UUID.randomUUID();
        UUID newerReceivedId = UUID.randomUUID();

        insertMessage(
                receivedId, conversationId, counterpartId, requesterId,
                "기준 이전에 받은 메시지", false,
                baseCreatedAt.minusMinutes(1), null
        );
        insertMessage(
                sentReferenceId, conversationId, requesterId, counterpartId,
                "요청자가 보낸 기준 메시지", false,
                baseCreatedAt, baseCreatedAt
        );
        insertMessage(
                newerReceivedId, conversationId, counterpartId, requesterId,
                "기준 이후에 받은 메시지", false,
                baseCreatedAt.plusMinutes(1),
                baseCreatedAt.plusMinutes(1)
        );

        Map<UUID, MessageSnapshot> before = findMessages();

        directMessageService.read(
                new DirectMessageReadCommand(
                        conversationId,
                        sentReferenceId,
                        requesterId
                )
        );

        Map<UUID, MessageSnapshot> after = findMessages();
        assertThat(after).containsOnlyKeys(
                receivedId,
                sentReferenceId,
                newerReceivedId
        );

        MessageSnapshot received = after.get(receivedId);
        assertThat(received.read).isTrue();
        assertThat(received.updatedAt).isNotNull();
        assertThat(after.get(sentReferenceId))
                .isEqualTo(before.get(sentReferenceId));
        assertThat(after.get(newerReceivedId))
                .isEqualTo(before.get(newerReceivedId));
    }

    @ParameterizedTest(name = "이미 읽은 수신 메시지가 기준={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("읽음 처리 대상이 없으면 정상 종료하고 모든 메시지 상태를 유지한다.")
    void read_preservesMessagesWhenNothingNeedsUpdating(boolean alreadyReadReference){
        UUID referenceId = UUID.randomUUID();
        UUID newerReceivedId = UUID.randomUUID();

        if (alreadyReadReference){
            insertMessage(referenceId, conversationId, counterpartId, requesterId,
                    "이미 읽은 기준 메시지", true,
                    baseCreatedAt, baseCreatedAt.plusSeconds(10));
        }else {
            insertMessage(referenceId, conversationId, requesterId, counterpartId,
                    "요청자가 보낸 기준 메시지", false,
                    baseCreatedAt, baseCreatedAt);
        }

        insertMessage(
                newerReceivedId, conversationId, counterpartId, requesterId,
                "기준 이후의 미읽음 메시지", false,
                baseCreatedAt.plusSeconds(1),
                baseCreatedAt.plusSeconds(1)
        );

        Map<UUID, MessageSnapshot> before = findMessages();
        directMessageService.read(new DirectMessageReadCommand(
                conversationId,
                referenceId,
                requesterId
        ));
        Map<UUID, MessageSnapshot> after = findMessages();
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("동일하거나 이전 기준으로 재요청해도 읽음 상태와 수정 시각을 유지한다.")
    void read_preservesStateForRepeatedOrOlderRequests(){
        UUID olderId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();

        insertMessage(
                olderId, conversationId, counterpartId, requesterId,
                "이전 수신 메시지", false,
                baseCreatedAt.minusMinutes(1), null
        );
        insertMessage(
                referenceId, conversationId, counterpartId, requesterId,
                "기준 수신 메시지", false,
                baseCreatedAt, null
        );
        insertMessage(
                newerId, conversationId, counterpartId, requesterId,
                "이후 수신 메시지", false,
                baseCreatedAt.plusMinutes(1), null
        );

        DirectMessageReadCommand command = new DirectMessageReadCommand(conversationId, referenceId, requesterId);
        directMessageService.read(command);

        Map<UUID, MessageSnapshot> afterFirstRead = findMessages();
        assertThat(afterFirstRead).containsOnlyKeys(olderId, referenceId, newerId);
        assertThat(afterFirstRead.get(olderId).read).isTrue();
        assertThat(afterFirstRead.get(olderId).updatedAt).isNotNull();
        assertThat(afterFirstRead.get(referenceId).read).isTrue();
        assertThat(afterFirstRead.get(referenceId).updatedAt).isNotNull();
        assertThat(afterFirstRead.get(newerId).read).isFalse();
        assertThat(afterFirstRead.get(newerId).updatedAt).isNull();

        directMessageService.read(command);
        assertThat(findMessages())
                .as("동일 기준 재요청은 첫 처리 결과를 유지한다.")
                .isEqualTo(afterFirstRead);

        directMessageService.read(new DirectMessageReadCommand(
                conversationId, olderId, requesterId
        ));

        assertThat(findMessages())
                .as("이전 기준 재요청도 첫 처리 결과를 유지한다.")
                .isEqualTo(afterFirstRead);
    }

    @ParameterizedTest(name = "두 번째 요청이 이전 기준={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("동일하거나 이전 기준의 동시 요청은 먼저 반영된 읽음 상태와 수정 시각을 유지한다.")
    void read_preservesStateForConcurrentRequests(boolean olderSecondReference) throws ExecutionException, InterruptedException, TimeoutException {
        UUID olderId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();

        insertMessage(olderId, conversationId, counterpartId, requesterId,
                "이전 수신 메시지", false,
                baseCreatedAt.minusMinutes(1), null);
        insertMessage(referenceId, conversationId, counterpartId, requesterId,
                "기준 수신 메시지", false,
                baseCreatedAt, null);
        insertMessage(newerId, conversationId, counterpartId, requesterId,
                "이후 수신 메시지", false,
                baseCreatedAt.plusMinutes(1), null);

        DirectMessageReadCommand firstCommand = new DirectMessageReadCommand(conversationId, referenceId, requesterId);
        DirectMessageReadCommand secondCommand = new DirectMessageReadCommand(conversationId,
                olderSecondReference ? olderId : referenceId, requesterId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CompletableFuture<Map<UUID, MessageSnapshot>> prepared = new CompletableFuture<>();
        CompletableFuture<Integer> secondBackendPid = new CompletableFuture<>();

        try {

            Future<?> firstRead = startReadAndHoldTransaction(executor, firstCommand, prepared, allowCommit);
            Map<UUID, MessageSnapshot> firstState = prepared.get(10, TimeUnit.SECONDS);

            assertThat(firstState).containsOnlyKeys(olderId, referenceId, newerId);
            assertThat(firstState.get(olderId).read).isTrue();
            assertThat(firstState.get(olderId).updatedAt).isNotNull();
            assertThat(firstState.get(referenceId).read).isTrue();
            assertThat(firstState.get(referenceId).updatedAt).isNotNull();
            assertThat(firstState.get(newerId).read).isFalse();
            assertThat(firstState.get(newerId).updatedAt).isNull();

            Future<?> secondRead = executor.submit(() -> {
                try {
                    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                    transaction.executeWithoutResult(status -> {
                        Integer pid = jdbcTemplate.queryForObject(
                                "SELECT pg_backend_pid()", Integer.class);
                        secondBackendPid.complete(pid);
                        directMessageService.read(secondCommand);

                    });
                } catch (RuntimeException | Error failure) {
                    secondBackendPid.completeExceptionally(failure);
                    throw failure;
                }
            });

            Integer pid = secondBackendPid.get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        Boolean waitingForLock = jdbcTemplate.queryForObject(
                                "SELECT cardinality(pg_blocking_pids(?)) > 0"
                                , Boolean.class, pid);
                        assertThat(waitingForLock)
                                .as("두 번째 읽음 요청의 DB 잠금 대기")
                                .isTrue();
                    });
            allowCommit.countDown();
            firstRead.get(10, TimeUnit.SECONDS);
            secondRead.get(10, TimeUnit.SECONDS);
            assertThat(findMessages()).isEqualTo(firstState);
        } finally {
            allowCommit.countDown();
            executor.shutdown();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                        .as("읽음 처리 작업 스레드 종료")
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("이미 조회한 메시지도 같은 트랜잭션에서 재조회하면 최신 읽음 상태와 수정 시각을 반환한다.")
    void read_returnsUpdatedStateWhenMessageWasAlreadyLoaded(){
        UUID olderId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();

        insertMessage(olderId, conversationId, counterpartId, requesterId,
                "미리 조회할 이전 메시지", false,
                baseCreatedAt.minusMinutes(1), null);
        insertMessage(referenceId, conversationId, counterpartId, requesterId,
                "기준 메시지", false,
                baseCreatedAt, null);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status ->{
            DirectMessage before = entityManager.find(DirectMessage.class, olderId);

            assertThat(before).isNotNull();
            assertThat(before.isRead()).isFalse();
            assertThat(before.getUpdatedAt()).isNull();

            directMessageService.read(
                    new DirectMessageReadCommand(conversationId, referenceId, requesterId)
            );
            DirectMessage reloaded = entityManager.find(DirectMessage.class, olderId);
            MessageSnapshot stored = findMessages().get(olderId);

            assertThat(stored.read).isTrue();
            assertThat(stored.updatedAt).isNotNull();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.isRead()).isTrue();
            assertThat(reloaded.getUpdatedAt()).isEqualTo(stored.updatedAt);
        });
    }

    @Test
    @DisplayName("일부 메시지를 읽으면 hasUnread를 유지하고 모든 수신 메시지를 읽으면 false로 변경한다.")
    void read_updatesConversationHasUnreadAfterPartialAndFullReads(){
        UUID firstReceivedId = UUID.randomUUID();
        UUID lastReceivedId = UUID.randomUUID();
        UUID sentId = UUID.randomUUID();

        insertMessage(
                firstReceivedId, conversationId, counterpartId, requesterId,
                "첫 번째 수신 메시지", false,
                baseCreatedAt, null
        );
        insertMessage(
                lastReceivedId, conversationId, counterpartId, requesterId,
                "마지막 수신 메시지", false,
                baseCreatedAt.plusMinutes(1), null
        );
        insertMessage(
                sentId, conversationId, requesterId, counterpartId,
                "상대방이 아직 읽지 않은 발신 메시지", false,
                baseCreatedAt.plusMinutes(2), null
        );

        ConversationFindByIdCondition condition = new ConversationFindByIdCondition(requesterId, conversationId);
        assertThat(conversationService.findById(condition).hasUnread())
                .isTrue();

        directMessageService.read(new DirectMessageReadCommand(
                conversationId, firstReceivedId, requesterId));
        assertThat(conversationService.findById(condition).hasUnread())
                .as("일부만 읽으면 미읽음 수신 메시지가 남아 있다.")
                .isTrue();

        directMessageService.read(new DirectMessageReadCommand(
                conversationId, lastReceivedId, requesterId));

        assertThat(conversationService.findById(condition).hasUnread())
                .as("수신 메시지를 모두 읽으면 미읽음 상태가 해제된다.")
                .isFalse();

        ConversationFindByIdCondition counterpartCondition = new ConversationFindByIdCondition(counterpartId, conversationId);
        assertThat(conversationService.findById(counterpartCondition).hasUnread())
                .as("상대방의 미읽음 상태는 유지된다.")
                .isTrue();
    }

    private void insertUser(UUID userId, String nickname) {
        jdbcTemplate.update("""
              INSERT INTO users ( id, email, nickname, role, enabled, created_at )
              VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
              """, userId, userId + "@test.example", nickname);
    }

    private void insertConversation(
            UUID conversationId,
            UUID firstUserId,
            UUID secondUserId,
            LocalDateTime createdAt
    ) {
        boolean firstUserComesFirst = firstUserId.toString().compareTo(secondUserId.toString()) < 0;

        UUID userAId = firstUserComesFirst ? firstUserId : secondUserId;
        UUID userBId = firstUserComesFirst ? secondUserId : firstUserId;

        jdbcTemplate.update("""
              INSERT INTO conversations ( id, user_a_id, user_b_id, created_at )
              VALUES (?, ?, ?, ?)
              """, conversationId, userAId, userBId, createdAt);
    }

    private void insertMessage(
            UUID messageId,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            boolean read,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO direct_messages ( id, conversation_id, sender_id, receiver_id,
                  content, is_read, created_at, updated_at )
              VALUES (?, ?, ?, ?, ?, ?, ?, ?)
              """, messageId, conversationId, senderId, receiverId, content, read, createdAt, updatedAt);
        }

    private record MessageSnapshot(
            UUID id,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            boolean read,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    private Map<UUID, MessageSnapshot> findMessages() {
        return jdbcTemplate.query("""
              SELECT id, conversation_id, sender_id, receiver_id, content, is_read, created_at, updated_at
              FROM direct_messages
              WHERE conversation_id IN (?, ?)
              """,(rs, rowNum) -> new
                        MessageSnapshot(
                        rs.getObject("id",UUID.class),
                        rs.getObject("conversation_id", UUID.class),
                        rs.getObject("sender_id",UUID.class),
                        rs.getObject("receiver_id",UUID.class),
                        rs.getString("content"),
                        rs.getBoolean("is_read"),
                        rs.getObject("created_at",LocalDateTime.class),
                        rs.getObject("updated_at",LocalDateTime.class)
                ),
                conversationId, otherConversationId).stream()
                .collect(Collectors.toMap(MessageSnapshot::id,
                        message -> message));
    }

    private Future<?> startReadAndHoldTransaction(
            ExecutorService executor,
            DirectMessageReadCommand command,
            CompletableFuture<Map<UUID, MessageSnapshot>> prepared,
            CountDownLatch allowCommit
    ) {
        return executor.submit(() -> {
            try {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> {
                    directMessageService.read(command);
                    entityManager.flush();
                    prepared.complete(findMessages());

                    try {
                        assertThat(allowCommit.await(30, TimeUnit.SECONDS))
                                .as("첫 번째 읽음 처리의 커밋 허용 신호")
                                .isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("첫 번째 읽음 처리의 대기가 중단됐습니다.", exception);
                    }
                });
            } catch (RuntimeException | Error failure) {
                prepared.completeExceptionally(failure);
                throw failure;
            }
        });
    }
}
