package com.codeit.sb13.playlist_for_everyone.domain.notification;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 실제 PostgreSQL·Flyway와 Spring 트랜잭션으로 공통 생성 요청부터 커밋 후 SSE 발행 요청까지 검증한다.
 *
 * <p>SseEventPublisher만 Mock으로 대체하므로 Redis·HTTP 수신은 이 클래스의 검증 범위가 아니다.
 * 테스트 전체를 @Transactional로 감싸지 않고 TransactionTemplate으로 커밋·롤백 시점을 제어한다.
 * 사용자 닉네임 변경은 알림 처리 실패에도 원래 업무의 커밋이 보존되는지 확인하기 위한 업무 대역이다.
 */
@RecordApplicationEvents
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("알림 공통 생성 통합 테스트")
class NotificationIntegrationTest {

    private static final UUID RECEIVER_ID = UUID.randomUUID();
    private static final String TITLE = "새 알림";
    private static final String CONTENT = "알림 내용";

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
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // DB와 리스너는 실제 빈을 사용하되, 외부 전송 경계의 호출 횟수·데이터·실패를 제어한다.
    @MockitoBean
    private SseEventPublisher sseEventPublisher;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // 새 트랜잭션에서 알림을 저장할 때 참조할 수 있도록 수신자를 먼저 커밋한다.
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, RECEIVER_ID, RECEIVER_ID + "@test.example", "변경 전");
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", RECEIVER_ID);
    }

    @Test
    @DisplayName("업무 커밋 후 알림을 저장하고 생성시각을 포함한 SSE 이벤트를 한 번 발행한다.")
    void savesNotificationOnlyAfterTheBusinessTransactionCommits(ApplicationEvents applicationEvents) {
        // 테스트 자체를 @Transactional로 감싸면 기본 롤백으로 AFTER_COMMIT을 검증할 수 없다.
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
            assertThat(notificationRepository.count()).isZero();
        });

        Notification saved = findOnlyNotification();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getReceiver()).isEqualTo(RECEIVER_ID);
        assertThat(saved.getTitle()).isEqualTo(TITLE);
        assertThat(saved.getContent()).isEqualTo(CONTENT);
        assertThat(saved.getLevel()).isEqualTo(NotificationLevel.INFO);

        List<NotificationCreatedEvent> events = applicationEvents
                .stream(NotificationCreatedEvent.class).toList();

        assertThat(events).hasSize(1);
        NotificationCreatedEvent createdEvent = events.get(0);
        assertThat(createdEvent.notificationDto().id())
                .isEqualTo(saved.getId());
        assertThat(createdEvent.notificationDto().createdAt())
                .isNotNull();

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(eventCaptor.capture());
        SseEvent<?> publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.eventId()).isEqualTo(createdEvent.eventId());
        assertThat(publishedEvent.receiverId()).isEqualTo(RECEIVER_ID);
        assertThat(publishedEvent.eventName()).isEqualTo("notifications");
        assertThat(publishedEvent.data()).isEqualTo(createdEvent.notificationDto());
    }

    @Test
    @DisplayName("업무 트랜잭션이 롤백되면 알림을 저장하지 않고 SSE를 발행하지 않는다.")
    void doesNotSaveNotificationWhenTheBusinessTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            changeReceiverNickname();
            eventPublisher.publishEvent(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
            status.setRollbackOnly();
        });

        assertThat(receiverNickname()).isEqualTo("변경 전");
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("트랜잭션 없이 발행한 이벤트로는 알림을 저장하지 않는다")
    void ignoresAnEventPublishedWithoutATransaction() {
        eventPublisher.publishEvent(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));

        assertThat(notificationRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationLevel.class)
    @DisplayName("모든 알림 등급을 DB에 문자열로 저장하고 같은 값으로 조회한다")
    void persistsEveryLevelAsAString(NotificationLevel level) {
        publishAndCommit(event(RECEIVER_ID, TITLE, level));

        Notification saved = findOnlyNotification();
        assertThat(saved.getLevel()).isEqualTo(level);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT level FROM notifications WHERE id = ?", String.class, saved.getId()))
                .isEqualTo(level.name());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"가", "😀"})
    @DisplayName("긴 제목은 이벤트 수신 후 이모지를 보존하며 255 코드 포인트로 줄여 저장한다")
    void persistsATruncatedTitleThroughTheEventListener(String character) {
        publishAndCommit(event(RECEIVER_ID, character.repeat(256), NotificationLevel.INFO));

        String savedTitle = findOnlyNotification().getTitle();
        assertThat(savedTitle).isEqualTo(character.repeat(252) + "...");
        assertThat(savedTitle.codePointCount(0, savedTitle.length())).isEqualTo(255);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    @DisplayName("필수 값이 누락되거나 공백이면 저장 서비스의 Bean Validation이 거부한다")
    void rejectsInvalidCommandsAtTheServiceBoundary(String description, NotificationCommand command) {
        assertThatThrownBy(() -> service.save(command))
                .isInstanceOf(ConstraintViolationException.class);

        assertThat(notificationRepository.count()).isZero();
    }

    static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of("요청 자체가 null", null),
                Arguments.of("수신자 ID가 null",
                        new NotificationCommand(null, TITLE, CONTENT, NotificationLevel.INFO)),
                Arguments.of("등급이 null",
                        new NotificationCommand(RECEIVER_ID, TITLE, CONTENT, null)),
                Arguments.of("제목이 null",
                        new NotificationCommand(RECEIVER_ID, null, CONTENT, NotificationLevel.INFO)),
                Arguments.of("제목이 빈 문자열",
                        new NotificationCommand(RECEIVER_ID, "", CONTENT, NotificationLevel.INFO)),
                Arguments.of("제목이 공백",
                        new NotificationCommand(RECEIVER_ID, " \t\n", CONTENT, NotificationLevel.INFO)),
                Arguments.of("내용이 null",
                        new NotificationCommand(RECEIVER_ID, TITLE, null, NotificationLevel.INFO)),
                Arguments.of("내용이 빈 문자열",
                        new NotificationCommand(RECEIVER_ID, TITLE, "", NotificationLevel.INFO)),
                Arguments.of("내용이 공백",
                        new NotificationCommand(RECEIVER_ID, TITLE, " \t\n", NotificationLevel.INFO))
        );
    }

    @Test
    @DisplayName("입력 검증 실패에도 업무 커밋을 유지하고 후속 정상 알림의 SSE만 발행한다.")
    void isolatesValidationFailureAndContinuesSavingNotifications(CapturedOutput output) {
        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            changeReceiverNickname();
            eventPublisher.publishEvent(event(RECEIVER_ID, " ", NotificationLevel.INFO));
        })).doesNotThrowAnyException();

        assertThat(receiverNickname()).isEqualTo("변경 후");
        assertThat(notificationRepository.count()).isZero();
        assertThat(output.getOut()).contains(
                "알림 저장 실패. receiverId=" + RECEIVER_ID, "ConstraintViolationException");

        verifyNoInteractions(sseEventPublisher);

        publishAndCommit(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
        assertThat(findOnlyNotification().getReceiver()).isEqualTo(RECEIVER_ID);

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue().receiverId()).isEqualTo(RECEIVER_ID);
    }

    @Test
    @DisplayName("알림 저장 실패에도 업무 커밋을 유지하고 후속 저장 알림의 SSE만 발행한다.")
    void isolatesDatabaseFailureAndContinuesSavingNotifications(CapturedOutput output) {
        UUID missingReceiverId = UUID.randomUUID();

        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            changeReceiverNickname();
            eventPublisher.publishEvent(event(missingReceiverId, TITLE, NotificationLevel.INFO));
        })).doesNotThrowAnyException();

        assertThat(receiverNickname()).isEqualTo("변경 후");
        assertThat(notificationRepository.count()).isZero();
        assertThat(output.getOut()).contains(
                "알림 저장 실패. receiverId=" + missingReceiverId, "DataIntegrityViolationException");

        verifyNoInteractions(sseEventPublisher);
        publishAndCommit(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
        assertThat(findOnlyNotification().getReceiver()).isEqualTo(RECEIVER_ID);

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue().receiverId())
                .isEqualTo(RECEIVER_ID);
    }

    @Test
    @DisplayName("트랜잭션 없이 발행한 알림 생성 이벤트로는 SSE를 발행하지 않는다.")
    void ignoresNotificationCreatedEventWithoutTransaction(){
        NotificationDto notification = new NotificationDto(UUID.randomUUID(), LocalDateTime.of(2026, 11, 2, 10, 0),
                RECEIVER_ID,
                TITLE,
                CONTENT,
                NotificationLevel.INFO);

        NotificationCreatedEvent createdEvent = new NotificationCreatedEvent(UUID.randomUUID(), notification);
        eventPublisher.publishEvent(createdEvent);
        verifyNoInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("SSE 발행 실패에도 업무 커밋과 저장된 알림을 유지하고 후속 알림을 저장·발행한다.")
    void preservesNotificationAndBusinessCommitWhenSsePublishFails(CapturedOutput output){
        SsePublishException failure = new SsePublishException(new IllegalStateException("테스트용 SSE 발행 실패"));
        doThrow(failure)
                .doNothing()
                .when(sseEventPublisher).publish(any());

        assertThatCode(()-> transactionTemplate.executeWithoutResult(status ->{
            changeReceiverNickname();
            eventPublisher.publishEvent(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
        })).doesNotThrowAnyException();

        assertThat(receiverNickname()).isEqualTo("변경 후");
        Notification saved = findOnlyNotification();
        assertThat(saved.getReceiver()).isEqualTo(RECEIVER_ID);
        assertThat(saved.getTitle()).isEqualTo(TITLE);
        verify(sseEventPublisher).publish(any());
        assertThat(output.getAll())
                .contains("알림 SSE 발행 실패.", "notificationId=" + saved.getId())
                .doesNotContain("알림 저장 실패.");

        String nextTitle = "후속 알림";
        publishAndCommit(event(RECEIVER_ID, nextTitle, NotificationLevel.INFO));

        assertThat(notificationRepository.findAll())
                .extracting(Notification::getTitle)
                .containsExactlyInAnyOrder(TITLE, nextTitle);

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher, times(2)).publish(eventCaptor.capture());

        SseEvent<?> nextPublishedEvent = eventCaptor.getAllValues().get(1);

        assertThat(nextPublishedEvent.receiverId()).isEqualTo(RECEIVER_ID);
        assertThat(nextPublishedEvent.eventName()).isEqualTo("notifications");
        assertThat(nextPublishedEvent.data())
                .isInstanceOfSatisfying(NotificationDto.class,
                        notification -> assertThat(notification.title()).isEqualTo(nextTitle));
    }

    @Test
    @DisplayName("알림 저장 트랜잭션이 커밋되기 전에는 SSE를 발행하지 않고 커밋 후 한 번 발행한다.")
    void publishesSseOnlyAfterNotificationTransactionCommits(){
        // 서비스의 REQUIRES_NEW는 반환 시 이미 커밋된다. 여기서는 저장 트랜잭션을 직접 열어
        // 내부의 미발행과 커밋 후 발행을 각각 관찰한다. 공통 생성 경로는 위 통합 사례에서 검증한다.
        transactionTemplate.executeWithoutResult(status ->{
            NotificationCommand command = new NotificationCommand(RECEIVER_ID, TITLE, CONTENT, NotificationLevel.INFO);

            Notification saved = notificationRepository.save(new Notification(command));
            eventPublisher.publishEvent(new NotificationCreatedEvent(UUID.randomUUID(), NotificationDto.from(saved)));

            verifyNoInteractions(sseEventPublisher);
        });

        assertThat(findOnlyNotification().getReceiver())
                .isEqualTo(RECEIVER_ID);
        verify(sseEventPublisher).publish(any());
    }

    @Test
    @DisplayName("알림 저장 트랜잭션이 롤백되면 알림이 남지 않고 SSE를 발행하지 않는다.")
    void doesNotPublishSseWhenNotificationTransactionRollsBack(){
        // 생성 이벤트가 등록되었더라도 저장 트랜잭션을 롤백하면 AFTER_COMMIT은 실행되지 않는다.
        transactionTemplate.executeWithoutResult(status ->{
            NotificationCommand command = new NotificationCommand(RECEIVER_ID, TITLE, CONTENT, NotificationLevel.INFO);
            Notification saved = notificationRepository.save(new Notification(command));

            eventPublisher.publishEvent(new NotificationCreatedEvent(UUID.randomUUID(), NotificationDto.from(saved)));
            status.setRollbackOnly();
        });

        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
    }

    private NotificationCreateRequestedEvent event(UUID receiverId, String title, NotificationLevel level) {
        return new NotificationCreateRequestedEvent(receiverId, title, CONTENT, level);
    }

    /** 업무 트랜잭션의 정상 종료를 재현하여 첫 번째 AFTER_COMMIT 리스너부터 실행한다. */
    private void publishAndCommit(NotificationCreateRequestedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private Notification findOnlyNotification() {
        // 테스트 전체를 감싸는 영속성 컨텍스트 없이 커밋된 데이터를 다시 조회한다.
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        return notifications.get(0);
    }

    private void changeReceiverNickname() {
        jdbcTemplate.update("UPDATE users SET nickname = ? WHERE id = ?", "변경 후", RECEIVER_ID);
    }

    private String receiverNickname() {
        return jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE id = ?", String.class, RECEIVER_ID);
    }
}
