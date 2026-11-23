package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener.NotificationEventListener;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 실제 DM 커밋과 알림 전용 실행기를 연결하여 요청 스레드의 비대기·제출 거부 격리를 검증한다.
 * 별도 호출 스레드는 요청을 재현하는 테스트 도구이며, 운영 알림 풀은 기존 빈 하나를 사용한다.
 * 알림 저장과 공통 리스너는 실제 동작을 유지하고 외부 전송·파일 조회만 Mock으로 분리한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.docker.compose.enabled=false",
        "everyone.notification.async.pool-size=2",
        "everyone.notification.async.queue-capacity=1"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM 알림 비동기 경계 통합 테스트")
class DirectMessageNotificationAsyncIntegrationTest {

    private static final long RESPONSE_TIMEOUT_SECONDS = 5;
    private static final long BLOCK_TIMEOUT_SECONDS = 30;

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
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
    @Qualifier("notificationTaskExecutor")
    private ThreadPoolTaskExecutor notificationTaskExecutor;

    @MockitoSpyBean
    private NotificationEventListener notificationEventListener;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private SseEventPublisher sseEventPublisher;

    @MockitoBean
    private DirectMessagePublisher directMessagePublisher;

    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final ExecutorService callerExecutor = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "dm-notification-test-request"));
    private final List<CompletableFuture<?>> trackedTasks = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        insertUser(senderId, "발신자");
        insertUser(receiverId, "수신자");

        boolean senderFirst = senderId.toString().compareTo(receiverId.toString()) < 0;
        jdbcTemplate.update("""
                INSERT INTO conversations (id, user_a_id, user_b_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, senderFirst ? senderId : receiverId, senderFirst ? receiverId : senderId);
    }

    @AfterEach
    void cleanUp() {
        try {
            awaitTrackedTasks();
            await().atMost(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() ->
                    notificationTaskExecutor.getActiveCount() == 0
                            && notificationTaskExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        } finally {
            stopCallerExecutor();
        }
        jdbcTemplate.update("DELETE FROM notifications WHERE receiver_id IN (?, ?)", senderId, receiverId);
        jdbcTemplate.update("DELETE FROM direct_messages WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", senderId, receiverId);
    }

    @Test
    @DisplayName("알림 작업이 지연되어도 DM 송신 결과를 반환하고 WebSocket 발행을 진행한다.")
    void returnsAndPublishesWebSocketWithoutWaitingForNotification() throws InterruptedException {
        CountDownLatch notificationStarted = new CountDownLatch(1);
        CountDownLatch releaseNotification = new CountDownLatch(1);
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        AtomicReference<Thread> notificationThread = new AtomicReference<>();

        doAnswer(invocation -> {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            trackedTasks.add(completed);
            notificationThread.set(Thread.currentThread());
            notificationStarted.countDown();
            try {
                waitForRelease(releaseNotification);
                invocation.callRealMethod();
                completed.complete(null);
                return null;
            } catch (Throwable failure) {
                // Spring 콜백에서 예외가 처리되더라도 테스트 스레드에서 실패를 확인한다.
                completed.completeExceptionally(failure);
                throw failure;
            }
        }).when(notificationEventListener).handleNotification(
                argThat(event -> receiverId.equals(event.receiverId())));

        try {
            CompletableFuture<MessageDto> response = track(CompletableFuture.supplyAsync(() -> {
                callerThread.set(Thread.currentThread());
                return directMessageService.send(command("알림 처리를 기다리지 않을 메시지"));
            }, callerExecutor));

            assertThat(notificationStarted.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("공통 알림 요청 리스너 진입").isTrue();
            assertThat(countSavedMessages()).as("알림 처리 전에 DM 커밋 유지").isEqualTo(1L);

            // 차단 해제 전에 반환되어야 한다. 비상 차단 제한 30초가 먼저 풀려 통과하지 않도록 5초만 기다린다.
            MessageDto result = awaitResponse(response);
            assertThat(result.conversationId()).isEqualTo(conversationId);
            assertThat(notificationThread.get()).isNotSameAs(callerThread.get());
            assertThat(notificationThread.get().getName()).startsWith("notification-");
            verify(directMessagePublisher).publish(result);
            verify(sseEventPublisher).publish(argThat(event ->
                    "direct-messages".equals(event.eventName()) && result.id().equals(event.eventId())));
            assertThat(countSavedNotifications()).isZero();
            verify(sseEventPublisher, never()).publish(
                    argThat(event -> "notifications".equals(event.eventName())));
        } finally {
            releaseNotification.countDown();
        }

        awaitTrackedTasks();
        assertThat(countSavedNotifications()).isEqualTo(1L);
        verify(notificationEventListener).handleNotification(
                argThat(event -> receiverId.equals(event.receiverId())));
        verify(sseEventPublisher).publish(argThat(event ->
                "notifications".equals(event.eventName()) && receiverId.equals(event.receiverId())));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("알림 작업 제출이 거부되어도 DM 커밋과 WebSocket 발행을 유지하고 실패를 기록한다.")
    void keepsMessageAndWebSocketWhenNotificationSubmissionRejected(CapturedOutput output)
            throws InterruptedException {
        CountDownLatch runningTasksStarted = new CountDownLatch(2);
        CountDownLatch releaseRunningTasks = new CountDownLatch(1);
        String content = "거부 로그에 노출하지 않을 메시지 본문";

        try {
            for (int index = 0; index < 2; index++) {
                track(CompletableFuture.runAsync(() -> {
                    runningTasksStarted.countDown();
                    waitForRelease(releaseRunningTasks);
                }, notificationTaskExecutor));
            }
            assertThat(runningTasksStarted.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> queued = track(CompletableFuture.runAsync(() -> {}, notificationTaskExecutor));
            assertThat(queued.isDone()).as("알림 실행기의 대기열을 채운 상태").isFalse();

            MessageDto result = awaitResponse(track(CompletableFuture.supplyAsync(
                    () -> directMessageService.send(command(content)), callerExecutor)));

            assertThat(result.content()).isEqualTo(content);
            assertThat(countSavedMessages()).isEqualTo(1L);
            verify(directMessagePublisher).publish(result);
            verify(sseEventPublisher).publish(argThat(event ->
                    "direct-messages".equals(event.eventName()) && result.id().equals(event.eventId())));
            assertThat(countSavedNotifications()).as("제출 거부 시 호출 스레드에서 알림을 저장하지 않음").isZero();
            verifyNoInteractions(notificationEventListener);
            verify(sseEventPublisher, never()).publish(
                    argThat(event -> "notifications".equals(event.eventName())));
            assertThat(output.getAll())
                    .contains(
                            "DM 알림 작업 제출 거부.",
                            "directMessageId=" + result.id(),
                            "conversationId=" + conversationId,
                            "receiverId=" + receiverId,
                            "exceptionType=TaskRejectedException"
                    )
                    .doesNotContain(content);
        } finally {
            releaseRunningTasks.countDown();
        }
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> task) {
        trackedTasks.add(task);
        return task;
    }

    private MessageDto awaitResponse(CompletableFuture<MessageDto> response) {
        try {
            return response.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("DM 송신 응답 대기 중 인터럽트가 발생했습니다.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("알림 완료를 기다리지 않고 DM 송신이 반환되어야 합니다.", exception);
        }
    }

    private void awaitTrackedTasks() {
        try {
            CompletableFuture.allOf(trackedTasks.toArray(CompletableFuture[]::new))
                    .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("테스트 작업 완료 대기 중 인터럽트가 발생했습니다.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("차단 해제 후 테스트 작업이 정상 완료되어야 합니다.", exception);
        }
    }

    private void waitForRelease(CountDownLatch release) {
        try {
            if (!release.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 작업의 차단 해제 시간을 초과했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 작업의 차단 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private void stopCallerExecutor() {
        callerExecutor.shutdown();
        try {
            if (!callerExecutor.awaitTermination(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                callerExecutor.shutdownNow();
                assertThat(callerExecutor.awaitTermination(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .as("테스트 요청 스레드 종료").isTrue();
            }
        } catch (InterruptedException exception) {
            callerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new AssertionError("테스트 요청 스레드 종료 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private DirectMessageSendCommand command(String content) {
        return new DirectMessageSendCommand(conversationId, senderId, content);
    }

    private long countSavedMessages() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM direct_messages WHERE conversation_id = ?", Long.class, conversationId);
    }

    private long countSavedNotifications() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE receiver_id = ?", Long.class, receiverId);
    }

    private void insertUser(UUID userId, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, userId, userId + "@test.example", nickname);
    }
}
