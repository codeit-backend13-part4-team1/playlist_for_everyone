package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageSaver;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener.NotificationEventListener;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.notification.support.NotificationFixtureCleanup;
import com.codeit.sb13.playlist_for_everyone.domain.notification.support.NotificationTaskTracker;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskDecorator;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.codeit.sb13.playlist_for_everyone.domain.notification.support.NotificationMetricReader.processingCount;
import static com.codeit.sb13.playlist_for_everyone.domain.notification.support.NotificationMetricReader.publishAttempts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 실제 저장 트랜잭션과 커밋 후 SSE 리스너를 연결하고, 외부 파일 조회·전송만 Mock으로 분리한다.
 * Repository와 알림 리스너 Spy는 실제 동작을 유지하고, 지정한 경계에서만 실패를 주입한다.
 * 커밋 후 콜백의 단언 실패가 Spring 내부에 흡수되지 않도록 관찰값은 호출과 알림 작업이 끝난 뒤 검증한다.
 *
 * <p>클래스 전체를 테스트 트랜잭션으로 감싸지 않고, 롤백·상위 트랜잭션 시나리오마다 TransactionTemplate을 사용한다.
 * 따라서 실제 커밋 여부와 AFTER_COMMIT 실행 시점을 구분할 수 있다. Reader가 반환되어도 콜백의 저장 측
 * 자원이 해제됐다고 가정하지 않는다.
 * 알림 소비자도 실제로 실행하므로 관찰·실패 주입·발행 횟수는 SSE 이벤트 이름으로 구분한다.
 * 워커에서 발생하는 알림 이벤트는 스레드 안전한 수집기로 관측하고, 작업 완료 후 데이터를 정리한다.
 * Redis 전송·HTTP 수신은 별도의 DirectMessageSseIntegrationTest에서 검증한다.
 */
@RecordApplicationEvents
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@Import(DirectMessageSseEventIntegrationTest.ObservationConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM 저장 후 SSE 이벤트 연동 통합 테스트")
class DirectMessageSseEventIntegrationTest {

    @RegisterExtension
    static final NotificationFixtureCleanup FIXTURE_CLEANUP = new NotificationFixtureCleanup();

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DirectMessageSaver directMessageSaver;

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("jpaAuditingHandler")
    private AuditingHandler auditingHandler;

    @Autowired
    @Qualifier("notificationTaskExecutor")
    private ThreadPoolTaskExecutor notificationTaskExecutor;
    @Autowired
    @Qualifier("notificationTaskContextDecorator")
    private TaskDecorator notificationTaskContextDecorator;

    @Autowired
    private NotificationEventProbe notificationEventProbe;

    private NotificationTaskTracker notificationTasks;

    @MockitoSpyBean
    private DirectMessageRepository directMessageRepository;

    @MockitoSpyBean
    private NotificationEventListener notificationEventListener;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private FileStorage fileStorage;

    @MockitoBean
    private SseEventPublisher sseEventPublisher;

    @MockitoBean
    private DirectMessagePublisher directMessagePublisher;

    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID profileImageId = UUID.randomUUID();
    private final String profileImageKey = "profiles/" + profileImageId + ".png";

    @BeforeEach
    void setUp() {
        notificationEventProbe.clear();
        notificationTasks = new NotificationTaskTracker(notificationTaskExecutor, notificationTaskContextDecorator);
        insertUser(senderId, "발신자");
        insertUser(receiverId, "수신자");

        boolean senderComesFirst = senderId.toString().compareTo(receiverId.toString()) < 0;
        UUID userAId = senderComesFirst ? senderId : receiverId;
        UUID userBId = senderComesFirst ? receiverId : senderId;

        jdbcTemplate.update("""
                INSERT INTO conversations (id, user_a_id, user_b_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, userAId, userBId);
    }

    @AfterEach
    void cleanUp() {
        try {
            FIXTURE_CLEANUP.cleanUp(notificationTasks, this::deleteFixtures);
        } finally {
            auditingHandler.setDateTimeProvider(null);
        }
    }

    private void deleteFixtures() {
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE receiver_id IN (?, ?)",
                senderId,
                receiverId
        );
        jdbcTemplate.update("DELETE FROM direct_messages WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", senderId, receiverId);
        jdbcTemplate.update("DELETE FROM file_infos WHERE id = ?", profileImageId);
    }

    @Test
    @DisplayName("실제 DM 커밋 후 미접속 상태에서도 알림을 한 건 저장하고 DM·알림 SSE 발행을 요청한다.")
    void save_publishesSseAfterCommit(ApplicationEvents applicationEvents) {
        // OS 시계 정밀도와 무관하게 저장 전 나노초와 DB 저장 시각의 차이를 재현한다.
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0, 0, 123456789);
        auditingHandler.setDateTimeProvider(() -> Optional.of(createdAt));
        AtomicReference<StoredMessage> visibleAtPublish = new AtomicReference<>();
        TransactionTemplate separateRead = new TransactionTemplate(transactionManager);
        separateRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        separateRead.setReadOnly(true);

        doAnswer(invocation -> {
            SseEvent<?> event = invocation.getArgument(0);
            visibleAtPublish.set(separateRead.execute(status -> findStoredMessage(event.eventId())));
            return null;
        }).when(sseEventPublisher).publish(argThat(event -> "direct-messages".equals(event.eventName())));

        double attemptsBefore = publishAttempts(meterRegistry, "direct_message_created");
        long processedBefore = processingCount(meterRegistry, "direct_message_created");

        DirectMessage saved = directMessageSaver.save(command("커밋된 메시지"));
        awaitNotificationTasks();

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(saved.getId()));
        StoredMessage stored = visibleAtPublish.get();
        assertThat(stored).isNotNull();
        assertThat(stored.id()).isEqualTo(saved.getId());
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.createdAt()).isNotEqualTo(createdAt);
        assertThat(saved.getCreatedAt()).isEqualTo(stored.createdAt());
        assertThat(stored.read()).isFalse();
        assertThat(countSavedMessages()).isEqualTo(1L);
        assertThat(countSavedNotifications()).isEqualTo(1L);

        NotificationDto notification = findStoredNotification();
        assertThat(notification).isNotNull();
        assertThat(notification.id()).isNotNull();
        assertThat(notification.createdAt()).isNotNull();
        assertThat(notification.receiverId()).isEqualTo(receiverId);
        assertThat(notification.title()).isEqualTo("새로운 메시지");
        assertThat(notification.content()).isEqualTo("발신자님이 메시지를 보냈습니다.");
        assertThat(notification.level()).isEqualTo(NotificationLevel.INFO);

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(published.eventId()).isEqualTo(stored.id());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.eventName()).isEqualTo("direct-messages");
        assertThat(published.data()).isEqualTo(new MessageDto(
                stored.id(), stored.conversationId(), stored.createdAt(),
                new UserSummary(stored.senderId(), "발신자", null),
                new UserSummary(stored.receiverId(), "수신자", null), stored.content()
        ));

        List<NotificationCreatedEvent> notificationEvents = notificationEventProbe.snapshot();
        assertThat(notificationEvents).hasSize(1);
        NotificationCreatedEvent notificationCreated = notificationEvents.get(0);

        SseEvent<?> notificationPublished = onlyPublishedEvent("notifications");
        assertThat(notificationPublished.eventId()).isEqualTo(notificationCreated.eventId());
        assertThat(notificationPublished.receiverId()).isEqualTo(receiverId);
        assertThat(notificationPublished.eventName()).isEqualTo("notifications");
        assertThat(notificationPublished.data()).isEqualTo(notificationCreated.notificationDto());

        NotificationDto delivered = notificationCreated.notificationDto();
        assertThat(delivered)
                .usingRecursiveComparison()
                .ignoringFields("createdAt")
                .isEqualTo(notification);
        assertThat(delivered.createdAt())
                .isCloseTo(notification.createdAt(), within(1_000, ChronoUnit.NANOS));

        verify(directMessageRepository).findDetailedById(saved.getId());
        verifyNoInteractions(fileStorage);

        assertSoftly(softly -> {
            softly.assertThat(publishAttempts(meterRegistry, "direct_message_created") - attemptsBefore)
                    .as("알림 발행 시도 증가량")
                    .isEqualTo(1.0);
            softly.assertThat(processingCount(meterRegistry, "direct_message_created") - processedBefore)
                    .as("알림 처리 시간 기록 증가량")
                    .isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("커밋 전과 롤백 후에는 DM 후속 조회·알림 저장·SSE 발행을 실행하지 않는다.")
    void doesNotProcessBeforeCommitOrAfterRollback(ApplicationEvents applicationEvents) {
        UUID directMessageId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            insertMessage(directMessageId, "롤백할 메시지");
            eventPublisher.publishEvent(event(directMessageId));

            assertThat(notificationTasks.hasSubmittedTasks()).as("커밋 전 알림 작업 미제출").isFalse();
            assertThat(countSavedNotifications()).isZero();
            assertThat(notificationEventProbe.snapshot()).isEmpty();

            // TransactionTemplate의 본문에서 실패한 검증은 호출한 테스트로 직접 전달된다.
            verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
            verifyNoInteractions(fileStorage, sseEventPublisher);
            status.setRollbackOnly();
        });

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(directMessageId));
        assertThat(countSavedMessages()).isZero();
        assertThat(countSavedNotifications()).isZero();
        assertThat(notificationTasks.hasSubmittedTasks()).as("롤백 후 알림 작업 미제출").isFalse();
        assertThat(notificationEventProbe.snapshot()).isEmpty();
        verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
        verifyNoInteractions(fileStorage, sseEventPublisher);
    }

    @Test
    @DisplayName("DB 저장이 실패하면 DM 이벤트가 발행됐어도 메시지·알림이 남지 않고 SSE를 발행하지 않는다.")
    void save_doesNotPublishWhenDatabaseCommitFails(ApplicationEvents applicationEvents) {
        String invalidContent = "DB 저장 실패 검증" + (char) 0;

        assertThatThrownBy(() -> directMessageSaver.save(command(invalidContent)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList()).hasSize(1);
        assertThat(notificationTasks.hasSubmittedTasks()).as("DM 커밋 실패 시 알림 작업 미제출").isFalse();
        assertThat(countSavedMessages()).isZero();
        assertThat(countSavedNotifications()).isZero();
        assertThat(notificationEventProbe.snapshot()).isEmpty();
        verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
        verifyNoInteractions(fileStorage, sseEventPublisher);
    }

    @Test
    @DisplayName("트랜잭션 없이 DM 이벤트를 발행하면 기존 메시지를 유지하고 알림 저장과 SSE 발행을 실행하지 않는다.")
    void doesNotProcessEventWithoutTransaction(ApplicationEvents applicationEvents) {
        UUID directMessageId = UUID.randomUUID();
        insertMessage(directMessageId, "이미 저장된 메시지");
        StoredMessage before = findStoredMessage(directMessageId);

        eventPublisher.publishEvent(event(directMessageId));

        assertThat(notificationTasks.hasSubmittedTasks()).as("트랜잭션 없는 업무 이벤트의 알림 작업 미제출").isFalse();
        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(directMessageId));
        assertThat(findStoredMessage(directMessageId)).isEqualTo(before);
        assertThat(countSavedNotifications()).isZero();
        assertThat(notificationEventProbe.snapshot()).isEmpty();
        verify(directMessageRepository, never()).findDetailedById(any(UUID.class));
        verifyNoInteractions(fileStorage, sseEventPublisher);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되어도 독립적으로 커밋한 DM·알림과 각각의 SSE 발행을 유지한다.")
    void save_keepsMessageAndSseWhenOuterTransactionRollsBack(ApplicationEvents applicationEvents) {
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        DirectMessage saved = outerTransaction.execute(status -> {
            DirectMessage message = directMessageSaver.save(command("외부 롤백 후에도 유지할 메시지"));
            jdbcTemplate.update("UPDATE users SET nickname = ? WHERE id = ?", "롤백할 이름", senderId);
            status.setRollbackOnly();
            return message;
        });
        awaitNotificationTasks();

        assertThat(saved).isNotNull();
        assertThat(countSavedMessages()).isEqualTo(1L);
        assertThat(countSavedNotifications()).isEqualTo(1L);
        NotificationDto notification = findStoredNotification();
        assertThat(findStoredMessage(saved.getId()).content()).isEqualTo("외부 롤백 후에도 유지할 메시지");
        assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, senderId))
                .isEqualTo("발신자");
        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(saved.getId()));

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(published.eventId()).isEqualTo(saved.getId());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class,
                dto -> assertThat(dto.id()).isEqualTo(saved.getId()));

        SseEvent<?> notificationPublished = onlyPublishedEvent("notifications");
        assertThat(notificationPublished.receiverId()).isEqualTo(receiverId);
        assertThat(notificationPublished.data())
                .isInstanceOfSatisfying(NotificationDto.class, dto -> {
                    assertThat(dto.id()).isEqualTo(notification.id());
                    assertThat(dto.receiverId()).isEqualTo(receiverId);
                });

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("커밋 후 메시지를 상세 조회한 뒤 프로필 URL과 SSE DTO를 구성해 발행한다.")
    void save_convertsDetailedMessageToSseAfterCommit() {
        insertReceiverProfileImage();
        String profileImageUrl = "https://example.com/profile.png";
        when(fileStorage.getFileUrl(profileImageKey)).thenReturn(profileImageUrl);

        DirectMessage saved = directMessageSaver.save(command("프로필이 포함된 메시지"));

        InOrder order = inOrder(directMessageRepository, fileStorage, sseEventPublisher);
        order.verify(directMessageRepository).findDetailedById(saved.getId());
        order.verify(fileStorage).getFileUrl(profileImageKey);
        order.verify(sseEventPublisher).publish(argThat(event -> "direct-messages".equals(event.eventName())));
        verifyNoMoreInteractions(fileStorage);

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(published.eventId()).isEqualTo(saved.getId());
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.sender()).isEqualTo(new UserSummary(senderId, "발신자", null));
            assertThat(dto.receiver()).isEqualTo(new UserSummary(receiverId, "수신자", profileImageUrl));
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("알림 요청 재발행에 실패해도 커밋된 DM을 유지하고 DM SSE와 WebSocket 발행을 실행한다.")
    void send_keepsMessageAndOtherChannelsWhenNotificationRequestPublishingFails(
            ApplicationEvents applicationEvents, CapturedOutput output) {
        doThrow(new IllegalStateException("테스트용 알림 요청 재발행 실패"))
                .when(notificationEventListener).handleNotification(any(NotificationCreateRequestedEvent.class));
        String content = "알림 요청 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));
        awaitNotificationTasks();

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(result.id()));
        verify(notificationEventListener)
                .handleNotification(argThat(event -> receiverId.equals(event.receiverId())));
        verify(notificationRepository, never()).save(any(Notification.class));
        assertThat(countSavedNotifications()).isZero();
        assertThat(notificationEventProbe.snapshot()).isEmpty();
        verify(sseEventPublisher, never())
                .publish(argThat(event -> "notifications".equals(event.eventName())));

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(published.eventId()).isEqualTo(result.id());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.id()).isEqualTo(result.id());
            assertThat(dto.content()).isEqualTo(content);
        });
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
        assertThat(output.getAll()).contains(
                "DM 알림 재발행 실패.",
                "directMessageId=" + result.id(),
                "conversationId=" + conversationId,
                "receiverId=" + receiverId,
                "exceptionType=IllegalStateException"
        );
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("알림 저장 호출에 실패해도 커밋된 DM을 유지하고 DM SSE와 WebSocket 발행을 실행한다.")
    void send_keepsMessageAndOtherChannelsWhenNotificationSaveFails(
            ApplicationEvents applicationEvents, CapturedOutput output) {
        doThrow(new DataIntegrityViolationException("테스트용 알림 저장 실패"))
                .when(notificationRepository).save(any(Notification.class));
        String content = "알림 저장 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));
        awaitNotificationTasks();

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(result.id()));
        verify(notificationEventListener)
                .handleNotification(argThat(event -> receiverId.equals(event.receiverId())));
        verify(notificationRepository).save(any(Notification.class));
        assertThat(countSavedNotifications()).isZero();
        assertThat(notificationEventProbe.snapshot()).isEmpty();
        verify(sseEventPublisher, never())
                .publish(argThat(event -> "notifications".equals(event.eventName())));

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(published.eventId()).isEqualTo(result.id());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.id()).isEqualTo(result.id());
            assertThat(dto.content()).isEqualTo(content);
        });
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
        assertThat(output.getAll()).contains(
                "알림 저장 실패. receiverId=" + receiverId,
                DataIntegrityViolationException.class.getSimpleName()
        );
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("알림 SSE 발행에 실패해도 DM·알림 저장을 유지하고 재시도 없이 DM SSE와 WebSocket을 발행한다.")
    void send_keepsMessageAndOtherChannelsWhenNotificationSsePublishingFails(
            ApplicationEvents applicationEvents, CapturedOutput output) {
        SsePublishException cause = new SsePublishException(new IllegalStateException("테스트용 알림 SSE 발행 실패"));
        doThrow(cause).when(sseEventPublisher)
                .publish(argThat(event -> "notifications".equals(event.eventName())));
        String content = "알림 SSE 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));
        awaitNotificationTasks();

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        assertThat(applicationEvents.stream(DirectMessageCreatedEvent.class).toList())
                .containsExactly(event(result.id()));
        SseEvent<?> notificationAttempted = assertStoredNotificationAndSseRequested();
        List<NotificationCreatedEvent> notificationEvents = notificationEventProbe.snapshot();
        assertThat(notificationEvents).hasSize(1);
        NotificationCreatedEvent notificationCreated = notificationEvents.get(0);
        assertThat(notificationAttempted.eventId()).isEqualTo(notificationCreated.eventId());
        assertThat(notificationAttempted.data()).isEqualTo(notificationCreated.notificationDto());

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(published.eventId()).isEqualTo(result.id());
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.id()).isEqualTo(result.id());
            assertThat(dto.content()).isEqualTo(content);
        });
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
        assertThat(output.getAll()).contains(
                "알림 SSE 발행 실패.",
                "notificationId=" + notificationCreated.notificationDto().id(),
                "eventId=" + notificationAttempted.eventId(),
                "receiverId=" + receiverId,
                "exceptionType=IllegalStateException"
        );
    }

    @Test
    @DisplayName("DM SSE용 메시지 조회에 실패해도 DM·알림 저장과 알림 SSE·WebSocket 발행을 유지한다.")
    void send_publishesWebSocketWhenSseMessageLookupFails() {
        doThrow(new DataAccessResourceFailureException("SSE용 메시지 조회 실패"))
                .when(directMessageRepository).findDetailedById(any(UUID.class));
        String content = "SSE 조회 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));
        awaitNotificationTasks();

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        verify(directMessageRepository).findDetailedById(result.id());
        verifyNoInteractions(fileStorage);
        verify(sseEventPublisher, never())
                .publish(argThat(event -> "direct-messages".equals(event.eventName())));
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        assertStoredNotificationAndSseRequested();
    }

    @Test
    @DisplayName("DM SSE용 프로필 URL 생성에 실패해도 DM·알림 저장과 알림 SSE·WebSocket 발행을 유지한다.")
    void send_publishesWebSocketWhenSseProfileUrlGenerationFails() {
        insertReceiverProfileImage();
        String profileImageUrl = "https://example.com/profile.png";
        IllegalStateException cause = new IllegalStateException("SSE용 프로필 URL 생성 실패");
        // 동기 SSE 리스너에서 먼저 변환하고, Saver 반환 후 송신 응답을 다시 변환한다.
        when(fileStorage.getFileUrl(profileImageKey))
                .thenThrow(cause)
                .thenReturn(profileImageUrl);
        String content = "SSE URL 생성 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));
        awaitNotificationTasks();

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.receiver()).isEqualTo(new UserSummary(receiverId, "수신자", profileImageUrl));
        verify(directMessageRepository).findDetailedById(result.id());
        verify(fileStorage, times(2)).getFileUrl(profileImageKey);
        verifyNoMoreInteractions(fileStorage);
        verify(sseEventPublisher, never())
                .publish(argThat(event -> "direct-messages".equals(event.eventName())));
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        assertStoredNotificationAndSseRequested();
    }

    @Test
    @DisplayName("DM SSE 발행에 실패해도 DM·알림 저장과 알림 SSE·WebSocket 발행을 재시도 없이 유지한다.")
    void send_publishesWebSocketWhenSsePublishingFails() {
        SsePublishException cause = new SsePublishException(new IllegalStateException("SSE 발행 실패"));
        doThrow(cause).when(sseEventPublisher)
                .publish(argThat(event -> "direct-messages".equals(event.eventName())));
        String content = "SSE 발행 실패 후에도 전달할 메시지";

        MessageDto result = directMessageService.send(command(content));
        awaitNotificationTasks();

        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(result.id());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(result.content()).isEqualTo(content);
        SseEvent<?> attempted = onlyPublishedEvent("direct-messages");
        assertThat(attempted.eventId()).isEqualTo(result.id());
        assertThat(attempted.receiverId()).isEqualTo(receiverId);
        assertThat(attempted.data()).isInstanceOfSatisfying(MessageDto.class,
                dto -> assertThat(dto.id()).isEqualTo(result.id()));
        verify(directMessagePublisher).publish(result);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
        assertStoredNotificationAndSseRequested();
    }

    @Test
    @DisplayName("WebSocket용 프로필 URL 생성에 실패하면 오류를 반환하고 DM·알림 저장과 각각의 SSE 발행을 유지한다.")
    void send_keepsSsePublicationWhenWebSocketProfileUrlGenerationFails() {
        insertReceiverProfileImage();
        String profileImageUrl = "https://example.com/profile.png";
        IllegalStateException cause = new IllegalStateException("WebSocket용 프로필 URL 생성 실패");
        // SSE 변환을 성공시킨 뒤 송신 응답 변환에서만 실패시킨다.
        when(fileStorage.getFileUrl(profileImageKey))
                .thenReturn(profileImageUrl)
                .thenThrow(cause);
        String content = "WebSocket URL 생성 실패 후에도 유지할 메시지";

        Throwable thrown = catchThrowable(() -> directMessageService.send(command(content)));

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(thrown).isInstanceOfSatisfying(DirectMessagePublishException.class, exception -> {
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getDetails())
                    .containsEntry("directMessageId", published.eventId())
                    .containsEntry("conversationId", conversationId)
                    .containsEntry("persisted", true);
        });
        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(published.eventId());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class, dto -> {
            assertThat(dto.id()).isEqualTo(stored.id());
            assertThat(dto.content()).isEqualTo(content);
            assertThat(dto.receiver()).isEqualTo(new UserSummary(receiverId, "수신자", profileImageUrl));
        });
        verify(fileStorage, times(2)).getFileUrl(profileImageKey);
        verifyNoMoreInteractions(fileStorage);
        verifyNoInteractions(directMessagePublisher);
        assertStoredNotificationAndSseRequested();
    }

    @Test
    @DisplayName("WebSocket 발행에 실패하면 오류를 반환하고 DM·알림 저장과 각각의 SSE 발행을 재시도 없이 유지한다.")
    void send_keepsSsePublicationWhenWebSocketPublishingFails() {
        IllegalStateException cause = new IllegalStateException("WebSocket 발행 실패");
        doAnswer(invocation -> {
            MessageDto message = invocation.getArgument(0);
            throw new DirectMessagePublishException(message.id(), message.conversationId(), cause);
        }).when(directMessagePublisher).publish(any(MessageDto.class));
        String content = "WebSocket 발행 실패 후에도 유지할 메시지";

        Throwable thrown = catchThrowable(() -> directMessageService.send(command(content)));

        SseEvent<?> published = onlyPublishedEvent("direct-messages");
        assertThat(thrown).isInstanceOfSatisfying(DirectMessagePublishException.class, exception -> {
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getDetails())
                    .containsEntry("directMessageId", published.eventId())
                    .containsEntry("conversationId", conversationId)
                    .containsEntry("persisted", true);
        });
        assertThat(countSavedMessages()).isEqualTo(1L);
        StoredMessage stored = findStoredMessage(published.eventId());
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.read()).isFalse();
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(MessageDto.class,
                dto -> assertThat(dto.id()).isEqualTo(stored.id()));
        ArgumentCaptor<MessageDto> messageCaptor = ArgumentCaptor.captor();
        verify(directMessagePublisher).publish(messageCaptor.capture());
        MessageDto attempted = messageCaptor.getValue();
        assertThat(attempted.id()).isEqualTo(stored.id());
        assertThat(attempted.conversationId()).isEqualTo(conversationId);
        assertThat(attempted.content()).isEqualTo(content);
        verifyNoMoreInteractions(directMessagePublisher);
        verifyNoInteractions(fileStorage);
        assertStoredNotificationAndSseRequested();
    }

    private DirectMessageSendCommand command(String content) {
        return new DirectMessageSendCommand(conversationId, senderId, content);
    }

    private DirectMessageCreatedEvent event(UUID directMessageId) {
        return new DirectMessageCreatedEvent(directMessageId, conversationId, senderId, "발신자", receiverId);
    }

    private SseEvent<?> onlyPublishedEvent(String eventName) {
        awaitNotificationTasks();
        ArgumentCaptor<SseEvent<?>> captor = ArgumentCaptor.captor();
        verify(sseEventPublisher, atLeastOnce()).publish(captor.capture());
        verifyNoMoreInteractions(sseEventPublisher);
        assertThat(captor.getAllValues()).extracting(SseEvent::eventName)
                .isSubsetOf("direct-messages", "notifications");

        List<SseEvent<?>> matchingEvents = captor.getAllValues().stream()
                .filter(event -> eventName.equals(event.eventName()))
                .toList();
        assertThat(matchingEvents).as("%s 이벤트 단건 발행", eventName).hasSize(1);
        return matchingEvents.get(0);
    }

    private SseEvent<?> assertStoredNotificationAndSseRequested() {
        awaitNotificationTasks();
        assertThat(countSavedNotifications()).isEqualTo(1L);
        verify(notificationRepository).save(any(Notification.class));
        NotificationDto stored = findStoredNotification();

        SseEvent<?> published = onlyPublishedEvent("notifications");
        assertThat(published.receiverId()).isEqualTo(receiverId);
        assertThat(published.data()).isInstanceOfSatisfying(NotificationDto.class, dto -> {
            assertThat(dto)
                    .usingRecursiveComparison()
                    .ignoringFields("createdAt")
                    .isEqualTo(stored);
            assertThat(dto.createdAt())
                    .isCloseTo(stored.createdAt(), within(1_000, ChronoUnit.NANOS));
        });
        return published;
    }

    private long countSavedMessages() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM direct_messages WHERE conversation_id = ?", Long.class, conversationId);
    }

    private void awaitNotificationTasks() {
        notificationTasks.awaitCompletion();
    }

    private long countSavedNotifications() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE receiver_id IN (?, ?)
                """,
                Long.class,
                senderId,
                receiverId
        );
    }

    private NotificationDto findStoredNotification() {
        return jdbcTemplate.queryForObject("""
                SELECT id, created_at, receiver_id, title, content, level
                FROM notifications
                WHERE receiver_id = ?
                """,
                (row, rowNum) -> new NotificationDto(
                        row.getObject("id", UUID.class),
                        row.getTimestamp("created_at").toLocalDateTime(),
                        row.getObject("receiver_id", UUID.class),
                        row.getString("title"),
                        row.getString("content"),
                        NotificationLevel.valueOf(row.getString("level"))
                ),
                receiverId
        );
    }

    private StoredMessage findStoredMessage(UUID directMessageId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, conversation_id, sender_id, receiver_id, content, created_at, is_read
                FROM direct_messages
                WHERE id = ? AND conversation_id = ?
                """, (row, rowNum) -> new StoredMessage(
                row.getObject("id", UUID.class), row.getObject("conversation_id", UUID.class),
                row.getObject("sender_id", UUID.class), row.getObject("receiver_id", UUID.class),
                row.getString("content"), row.getTimestamp("created_at").toLocalDateTime(),
                row.getBoolean("is_read")
        ), directMessageId, conversationId);
    }

    private void insertUser(UUID userId, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, userId, userId + "@test.example", nickname);
    }

    private void insertMessage(UUID directMessageId, String content) {
        jdbcTemplate.update("""
                INSERT INTO direct_messages (
                    id, conversation_id, sender_id, receiver_id, content, is_read, created_at
                )
                VALUES (?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP)
                """, directMessageId, conversationId, senderId, receiverId, content);
    }

    private void insertReceiverProfileImage() {
        jdbcTemplate.update("""
                INSERT INTO file_infos (id, file_key, original_filename, content_type, size, created_at)
                VALUES (?, ?, 'profile.png', 'image/png', 1024, CURRENT_TIMESTAMP)
                """, profileImageId, profileImageKey);
        jdbcTemplate.update("UPDATE users SET profile_image_id = ? WHERE id = ?", profileImageId, receiverId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean
        NotificationEventProbe notificationEventProbe() {
            return new NotificationEventProbe();
        }
    }

    static class NotificationEventProbe {
        private final ConcurrentLinkedQueue<NotificationCreatedEvent> events = new ConcurrentLinkedQueue<>();

        @EventListener
        public void onCreated(NotificationCreatedEvent event) {
            events.add(event);
        }

        List<NotificationCreatedEvent> snapshot() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }

    private record StoredMessage(
            UUID id,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            LocalDateTime createdAt,
            boolean read
    ) {
    }
}
