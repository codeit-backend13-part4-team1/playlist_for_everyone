package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationAsyncProcessor;
import com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring.NotificationMetrics;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationTargetReader;
import com.codeit.sb13.playlist_for_everyone.domain.notification.support.NotificationTaskTracker;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.global.config.NotificationAsyncConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 실제 JDBC 커밋·업무 리스너·알림 실행기를 연결하고 공통 요청 수신에서 작업을 차단한다.
 * 저장·SSE 동작은 기존 도메인 통합 테스트가, DM 응답 경계는 DM 전용 비동기 테스트가 검증한다.
 */
@SpringJUnitConfig(classes = {
        NotificationAsyncConfig.class,
        NotificationAsyncProcessor.class,
        NotificationDomainEventListener.class,
        NotificationBusinessAsyncIntegrationTest.TransactionConfig.class
})
@TestPropertySource(properties = {
        "everyone.notification.async.pool-size=2",
        "everyone.notification.async.queue-capacity=1"
})
@DisplayName("업무 이벤트별 알림 비동기 발행 계약")
class NotificationBusinessAsyncIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    @Qualifier("notificationTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    @Autowired
    @Qualifier("notificationTaskContextDecorator")
    private TaskDecorator notificationTaskContextDecorator;
    @MockitoBean
    private NotificationTargetReader targetReader;
    @MockitoBean
    private NotificationMetrics metrics;
    @MockitoBean
    private NotificationEventListener notificationEventListener;

    private final UUID businessId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID firstReceiverId = UUID.randomUUID();
    private final UUID secondReceiverId = UUID.randomUUID();
    private final List<CompletableFuture<?>> tasks = new CopyOnWriteArrayList<>();
    private final ExecutorService caller = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS business_markers (id UUID PRIMARY KEY)");
    }

    @AfterEach
    void cleanUp() throws Exception {
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        } finally {
            caller.shutdownNow();
            assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            jdbcTemplate.update("DELETE FROM business_markers WHERE id = ?", businessId);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Source.class)
    @DisplayName("알림 처리를 기다리지 않고 업무 커밋이 반환되며 요청 목록을 한 작업에서 순차 발행한다.")
    void submitsOneBatchWithoutWaitingForNotifications(Source source) throws Exception {
        Object event = event(source);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        List<Thread> notificationThreads = new CopyOnWriteArrayList<>();
        List<UUID> recipients = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            notificationThreads.add(Thread.currentThread());
            started.countDown();
            awaitRelease(release);
            recipients.add(invocation.<NotificationCreateRequestedEvent>getArgument(0).receiverId());
            return null;
        }).when(notificationEventListener).handleNotification(any());

        try (NotificationTaskTracker tracker = new NotificationTaskTracker(executor, notificationTaskContextDecorator)) {
            CompletableFuture<Void> committed = track(CompletableFuture.runAsync(() -> {
                callerThread.set(Thread.currentThread());
                publishInTransaction(event);
            }, caller));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(markerCount()).isEqualTo(1);
                // 차단 해제(최대 30초) 전에 커밋 호출이 반환되어야 한다.
                committed.get(5, TimeUnit.SECONDS);
                assertThat(recipients).isEmpty();
                assertThat(notificationThreads.get(0)).isNotSameAs(callerThread.get());
                assertThat(notificationThreads.get(0).getName()).startsWith("notification-");
                assertThat(tracker.submittedTaskCount()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            committed.get(5, TimeUnit.SECONDS);
            tracker.awaitCompletion();
            assertThat(recipients).containsExactlyElementsOf(receivers(source));
            assertThat(notificationThreads).allMatch(thread -> thread == notificationThreads.get(0));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Source.class)
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("실행기가 포화되면 요청 목록 전체를 거부하고 업무 커밋과 식별자 로그를 유지한다.")
    void rejectsWholeBatchWithoutRunningOnCaller(Source source, CapturedOutput output) throws Exception {
        Object event = event(source);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int index = 0; index < 2; index++) {
                CountDownLatch started = new CountDownLatch(1);
                track(CompletableFuture.runAsync(() -> {
                    started.countDown();
                    awaitRelease(release);
                }, executor));
                // 이미 생성된 풀에서도 다음 작업을 넣기 전에 큐에서 꺼낸 것을 확인한다.
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            }
            CompletableFuture<Void> queued = track(CompletableFuture.runAsync(() -> {}, executor));
            assertThat(queued.isDone()).isFalse();

            track(CompletableFuture.runAsync(() -> publishInTransaction(event), caller)).get(5, TimeUnit.SECONDS);

            assertThat(markerCount()).isEqualTo(1);
            verifyNoInteractions(notificationEventListener);
            assertThat(output.getAll())
                    .contains(source.label + " 알림 작업 제출 거부.",
                            "requestCount=" + receivers(source).size(),
                            "exceptionType=TaskRejectedException")
                    .doesNotContain("비공개 이름", "비공개 제목", "비공개 콘텐츠");
        } finally {
            release.countDown();
        }
    }

    private Object event(Source source) {
        return switch (source) {
            case FOLLOW -> new UserFollowedEvent(actorId, "비공개 이름", firstReceiverId);
            case ROLE_CHANGED -> new UserRoleChangedEvent(firstReceiverId, Role.USER, Role.ADMIN);
            case SUBSCRIBED -> new PlaylistSubscribedEvent(businessId, "비공개 제목", firstReceiverId, actorId, "비공개 이름");
            case PLAYLIST_CREATED -> {
                when(targetReader.findFollowerIds(actorId)).thenReturn(receivers(source));
                yield new PlaylistCreatedEvent(businessId, "비공개 제목", actorId, "비공개 이름");
            }
            case CONTENT_ADDED -> {
                when(targetReader.findSubscriberIds(businessId)).thenReturn(receivers(source));
                yield new PlaylistContentAddedEvent(businessId, "비공개 제목", UUID.randomUUID(), "비공개 콘텐츠", actorId);
            }
        };
    }

    private List<UUID> receivers(Source source) {
        return source == Source.PLAYLIST_CREATED || source == Source.CONTENT_ADDED
                ? List.of(firstReceiverId, secondReceiverId) : List.of(firstReceiverId);
    }

    private void publishInTransaction(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO business_markers (id) VALUES (?)", businessId);
            eventPublisher.publishEvent(event);
        });
    }

    private int markerCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_markers WHERE id = ?", Integer.class, businessId);
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> task) {
        tasks.add(task);
        return task;
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 작업 차단 해제 시간을 초과했습니다.");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 작업 차단 중 인터럽트가 발생했습니다.", failure);
        }
    }

    enum Source {
        FOLLOW("팔로우"), ROLE_CHANGED("권한 변경"), SUBSCRIBED("플레이리스트 구독"),
        PLAYLIST_CREATED("플레이리스트 생성"), CONTENT_ADDED("콘텐츠 추가");

        final String label;
        Source(String label) { this.label = label; }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfig {
        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(EmbeddedDatabase dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(EmbeddedDatabase dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
