package com.codeit.sb13.playlist_for_everyone.domain.notification;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener.NotificationEventListener;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationUserReader;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 실제 PostgreSQL·Flyway와 Spring 빈으로 알림 저장 및 커밋 후 SSE 발행 요청을 검증한다.
 * 직접 발행한 이벤트의 처리 경계와 실제 팔로우 등록부터 알림 저장까지의 연동을 함께 확인한다.
 *
 * <p>SseEventPublisher는 Mock으로 대체하여 발행 요청을 확인한다. Redis·HTTP 수신은 범위 밖이다.
 * 공통 요청 리스너의 Spy는 기본적으로 실제 메서드를 실행하며, 재발행 실패 사례에서만 예외를 유도한다.
 * 업무 이벤트를 변환하는 NotificationDomainEventListener는 실제 빈을 사용한다.
 *
 * <p>직접 이벤트를 발행하는 사례는 TransactionTemplate으로 커밋·롤백을 제어한다.
 * 실제 팔로우 등록 사례는 FollowSaver의 REQUIRES_NEW 커밋을 사용한다.
 * 테스트 전체를 @Transactional로 감싸지 않으며, 직접 발행 사례의 닉네임 변경은 업무 대역이다.
 *
 * <p>입력 검증·사용자 조회·DB 저장의 실패 지점은 실제 저장 서비스를 통해 구분한다.
 * 각 실패가 업무 커밋을 보존하고, 후속 정상 알림의 저장·SSE 발행을 막지 않는지 확인한다.
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
    private NotificationUserReader notificationUserReader;

    @Autowired
    private NotificationService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowRepository followRepository;

    // 외부 전송 경계의 호출 횟수·데이터·실패를 제어하며 Redis나 브라우저로는 전송하지 않는다.
    @MockitoBean
    private SseEventPublisher sseEventPublisher;

    // 기본 동작은 유지하고, 공통 요청 수신 중 예외를 던져 재발행 실패를 재현할 때만 대체한다.
    @MockitoSpyBean
    private NotificationEventListener notificationEventListener;

    private TransactionTemplate transactionTemplate;
    private static final UUID FOLLOWER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        // 팔로우와 알림의 독립 트랜잭션에서 조회할 수 있도록 두 사용자를 먼저 커밋한다.
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, RECEIVER_ID, RECEIVER_ID + "@test.example", "변경 전");
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, FOLLOWER_ID, FOLLOWER_ID + "@test.example", "홍길동");

    }

    @AfterEach
    void cleanUp() {
        // 서비스가 실제 커밋한 행을 정리한다. 두 테이블이 사용자를 참조하므로 사용자 삭제 전에 지운다.
        notificationRepository.deleteAllInBatch();
        followRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", RECEIVER_ID , FOLLOWER_ID);
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
        assertThat(saved.getReceiverId()).isEqualTo(RECEIVER_ID);
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
    @DisplayName("트랜잭션 없이 발행한 공통 생성 요청도 알림을 저장하고 SSE 발행을 한 번 요청한다.")
    void savesNotificationAndRequestsSseWithoutBusinessTransaction() {
        eventPublisher.publishEvent(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));

        Notification saved = findOnlyNotification();
        assertThat(saved.getReceiverId()).isEqualTo(RECEIVER_ID);
        assertThat(saved.getTitle()).isEqualTo(TITLE);
        assertThat(saved.getContent()).isEqualTo(CONTENT);
        assertThat(saved.getLevel()).isEqualTo(NotificationLevel.INFO);

        verify(sseEventPublisher).publish(any());
    }

    @Test
    @DisplayName("팔로우 이벤트를 발행한 트랜잭션의 커밋 후 알림을 저장한다.")
    void savesNotificationFromFollowEventAfterBusinessCommit(ApplicationEvents applicationEvents) {
        // 이벤트를 직접 발행해 커밋 전후의 소비 시점을 관찰한다. 실제 팔로우 등록은 별도 사례로 검증한다.
        UserFollowedEvent event = new UserFollowedEvent(UUID.randomUUID(), "홍길동", RECEIVER_ID);
        NotificationCreateRequestedEvent expectedRequest = new NotificationCreateRequestedEvent(
                RECEIVER_ID,
                "새로운 팔로워",
                "홍길동님이 회원님을 팔로우했습니다.",
                NotificationLevel.INFO
        );

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);

            assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class)).isEmpty();
            assertThat(notificationRepository.count()).isZero();
            verifyNoInteractions(sseEventPublisher);
        });

        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .containsExactly(expectedRequest);

        Notification saved = findOnlyNotification();
        assertThat(saved.getReceiverId()).isEqualTo(expectedRequest.receiverId());
        assertThat(saved.getTitle()).isEqualTo(expectedRequest.title());
        assertThat(saved.getContent()).isEqualTo(expectedRequest.content());
        assertThat(saved.getLevel()).isEqualTo(expectedRequest.level());

        verify(sseEventPublisher).publish(any());
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
        assertThat(findOnlyNotification().getReceiverId()).isEqualTo(RECEIVER_ID);

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue().receiverId()).isEqualTo(RECEIVER_ID);
    }

    @Test
    @DisplayName("수신자가 없어도 업무 커밋을 유지하고 후속 정상 알림만 저장하여 SSE를 발행한다.")
    void isolatesMissingReceiverFailureAndContinuesSavingNotifications(CapturedOutput output) {
        UUID missingReceiverId = UUID.randomUUID();

        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            changeReceiverNickname();
            eventPublisher.publishEvent(event(missingReceiverId, TITLE, NotificationLevel.INFO));
        })).doesNotThrowAnyException();

        assertThat(receiverNickname()).isEqualTo("변경 후");
        assertThat(notificationRepository.count()).isZero();
        assertThat(output.getOut()).contains(
                "알림 저장 실패. receiverId=" + missingReceiverId, UserNotFoundException.class.getSimpleName());

        verifyNoInteractions(sseEventPublisher);
        publishAndCommit(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
        assertThat(findOnlyNotification().getReceiverId()).isEqualTo(RECEIVER_ID);

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
        assertThat(saved.getReceiverId()).isEqualTo(RECEIVER_ID);
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

            Notification saved = notificationRepository.save(new Notification(command, notificationUserReader.getById(RECEIVER_ID)));
            eventPublisher.publishEvent(new NotificationCreatedEvent(UUID.randomUUID(), NotificationDto.from(saved)));

            verifyNoInteractions(sseEventPublisher);
        });

        assertThat(findOnlyNotification().getReceiverId())
                .isEqualTo(RECEIVER_ID);
        verify(sseEventPublisher).publish(any());
    }

    @Test
    @DisplayName("알림 저장 트랜잭션이 롤백되면 알림이 남지 않고 SSE를 발행하지 않는다.")
    void doesNotPublishSseWhenNotificationTransactionRollsBack(){
        // 생성 이벤트가 등록되었더라도 저장 트랜잭션을 롤백하면 AFTER_COMMIT은 실행되지 않는다.
        transactionTemplate.executeWithoutResult(status ->{
            NotificationCommand command = new NotificationCommand(RECEIVER_ID, TITLE, CONTENT, NotificationLevel.INFO);
            Notification saved = notificationRepository.save(new Notification(command, notificationUserReader.getById(RECEIVER_ID)));

            eventPublisher.publishEvent(new NotificationCreatedEvent(UUID.randomUUID(), NotificationDto.from(saved)));
            status.setRollbackOnly();
        });

        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("DB 저장 실패에도 업무 커밋을 유지하고 후속 정상 알림만 저장하여 SSE를 발행한다.")
    void isolatesDatabaseFailureAndContinuesSavingNotifications(CapturedOutput output){
        // Java null이 아닌 NUL 문자를 정상 본문에 덧붙여 입력 검증은 통과하고 PostgreSQL 저장은 실패하게 한다.
        // 수신자 미존재로 저장 전에 종료되는 사례와 별도로 실제 DB 실패의 격리를 확인한다.
        String invalidContent = CONTENT + (char) 0;

        assertThatCode(()->
                transactionTemplate.executeWithoutResult(status ->{
                    changeReceiverNickname();

                    eventPublisher.publishEvent(new NotificationCreateRequestedEvent(
                            RECEIVER_ID,
                            TITLE,
                            invalidContent,
                            NotificationLevel.INFO
                    ));
                })).doesNotThrowAnyException();

        assertThat(output.getOut()).contains("알림 저장 실패. receiverId=" + RECEIVER_ID,
                DataIntegrityViolationException.class.getSimpleName());
        assertThat(receiverNickname()).isEqualTo("변경 후");
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);

        publishAndCommit(event(RECEIVER_ID, TITLE, NotificationLevel.INFO));
        Notification saved = findOnlyNotification();
        assertThat(saved.getReceiverId()).isEqualTo(RECEIVER_ID);
        assertThat(saved.getContent()).isEqualTo(CONTENT);

        ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.captor();

        verify(sseEventPublisher).publish(eventCaptor.capture());

        SseEvent<?> publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.receiverId()).isEqualTo(RECEIVER_ID);
        assertThat(publishedEvent.data()).isInstanceOfSatisfying(NotificationDto.class,
                actual -> {
                    assertThat(actual)
                            .usingRecursiveComparison()
                            .ignoringFields("createdAt")
                            .isEqualTo(NotificationDto.from(saved));

                    assertThat(actual.createdAt())
                            .isCloseTo(saved.getCreatedAt(), within(1_000, ChronoUnit.NANOS));
                });

    }

    @Test
    @DisplayName("팔로우 이벤트를 발행한 트랜잭션이 롤백되면 공통 알림 요청을 발행하지 않는다.")
    void doesNotCreateNotificationWhenFollowEventTransactionRollsBack(ApplicationEvents applicationEvents){
        // 닉네임 변경과 이벤트 발행을 같은 트랜잭션에 넣어 생산 업무의 롤백을 재현한다.
        UserFollowedEvent event = new UserFollowedEvent(UUID.randomUUID(), "홍길동", RECEIVER_ID);
        transactionTemplate.executeWithoutResult(status ->{
            changeReceiverNickname();
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        assertThat(applicationEvents.stream(UserFollowedEvent.class))
                .containsExactly(event);
        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .isEmpty();
        assertThat(receiverNickname()).isEqualTo("변경 전");
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);

    }

    @Test
    @DisplayName("트랜잭션 없이 발행한 팔로우 이벤트는 공통 알림 요청으로 변환하지 않는다.")
    void ignoresFollowEventPublishedWithoutTransaction(ApplicationEvents applicationEvents){
        UserFollowedEvent event = new UserFollowedEvent(UUID.randomUUID(), "홍길동", RECEIVER_ID);

        eventPublisher.publishEvent(event);
        assertThat(applicationEvents.stream(UserFollowedEvent.class))
                .containsExactly(event);
        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .isEmpty();
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("실제 팔로우 등록이 커밋되면 대상 사용자에게 알림을 저장하고 SSE 발행을 한 번 요청한다.")
    void savesNotificationAfterActualFollowRegistrationCommits(){
        FollowCommand command = new FollowCommand(RECEIVER_ID, FOLLOWER_ID);

        // FollowSaver의 독립 트랜잭션이 커밋된 뒤 반환되므로 별도 TransactionTemplate으로 감싸지 않는다.
        FollowResult result = followService.follow(command);
        assertThat(result.created()).isTrue();
        assertThat(followRepository.existsById(result.followDto().id())).isTrue();
        assertThat(followRepository.count()).isEqualTo(1L);

        Notification saved = findOnlyNotification();
        assertThat(saved.getReceiverId()).isEqualTo(RECEIVER_ID);
        assertThat(saved.getTitle()).isEqualTo("새로운 팔로워");
        assertThat(saved.getContent()).isEqualTo("홍길동님이 회원님을 팔로우했습니다.");
        assertThat(saved.getLevel()).isEqualTo(NotificationLevel.INFO);
        verify(sseEventPublisher).publish(any());
    }

    @Test
    @DisplayName("알림 요청 재발행이 실패해도 커밋된 팔로우를 유지하고 실패를 기록한다.")
    void keepsFollowWhenNotificationRequestPublishFails(CapturedOutput output){
        // 공통 요청을 수신하는 쪽에서 예외를 던져 실제 업무 이벤트 리스너의 재발행 실패 처리를 확인한다.
        doThrow(new IllegalStateException("테스트용 알림 요청 재발행 실패"))
                .when(notificationEventListener)
                .handleNotification(any(NotificationCreateRequestedEvent.class));

        FollowResult result = followService.follow(new FollowCommand(RECEIVER_ID, FOLLOWER_ID));
        assertThat(result.created()).isTrue();
        assertThat(followRepository.existsById(result.followDto().id())).isTrue();
        assertThat(followRepository.count()).isEqualTo(1L);

        verify(notificationEventListener)
                .handleNotification(any(NotificationCreateRequestedEvent.class));
        assertThat(notificationRepository.count()).isZero();

        verifyNoInteractions(sseEventPublisher);
        // 팔로우 유지와 함께 도메인 리스너가 예외를 처리하고 기록한 사실까지 확인한다.
        assertThat(output.getOut()).contains(
                "팔로우 알림 변환·재발행 실패.",
                "followerId=" + FOLLOWER_ID,
                "followeeId=" + RECEIVER_ID,
                "exceptionType=IllegalStateException"
        );

    }

    private NotificationCreateRequestedEvent event(UUID receiverId, String title, NotificationLevel level) {
        return new NotificationCreateRequestedEvent(receiverId, title, CONTENT, level);
    }

    /** 공통 생성 요청을 트랜잭션 안에서 직접 발행하고 커밋해 NotificationEventListener부터 실행한다. */
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
