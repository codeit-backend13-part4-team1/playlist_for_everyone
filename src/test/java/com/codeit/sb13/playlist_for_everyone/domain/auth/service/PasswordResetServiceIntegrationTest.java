package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.config.PasswordResetMailAsyncConfig;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.PasswordResetMailSender;
import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.TemporaryPasswordMailListener;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.PasswordEncoderConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetTooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

/**
 * 실제 트랜잭션에서 발급·파기와 커밋 뒤 메일 발송을 검증한다.
 * 테스트 트랜잭션을 끄고 서비스가 직접 커밋하게 한다. 메일 발송만 Mock으로 대체한다.
 * 메일은 다른 스레드에서 나가므로 결과는 기다렸다가 확인한다.
 */
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        PasswordEncoderConfig.class,
        PasswordResetService.class,
        TemporaryPasswordGenerator.class,
        TemporaryPasswordMailListener.class,
        PasswordResetMailAsyncConfig.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("PostgreSQL 비밀번호 초기화 트랜잭션")
class PasswordResetServiceIntegrationTest {

    private static final String EMAIL = "reset@test.com";
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TemporaryPasswordRepository temporaryPasswordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PasswordResetMailSender passwordResetMailSender;

    @Autowired
    @Qualifier(PasswordResetMailAsyncConfig.EXECUTOR_NAME)
    private ThreadPoolTaskExecutor mailExecutor;


    @AfterEach
    void cleanUp() {
        // 테스트 트랜잭션이 없어서 자동 롤백되지 않는다. temporary_passwords가 users를 참조하므로 먼저 지운다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            temporaryPasswordRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });
    }

    @Test
    @DisplayName("다시 요청하면 이전 임시 비밀번호가 DB에서 파기되고 새 것 하나만 사용 가능하다.")
    void 다시_요청하면_이전_임시_비밀번호가_파기된다() {
        UUID userId = saveUser();

        passwordResetService.resetPassword(EMAIL);
        moveIssuedAtToPast();
        passwordResetService.resetPassword(EMAIL);

        List<TemporaryPassword> all = temporaryPasswordRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(TemporaryPassword::getStatus)
                .containsExactlyInAnyOrder(TemporaryPasswordStatus.ACTIVE, TemporaryPasswordStatus.DISCARDED);
        assertThat(temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE))
                .hasSize(1);
        await().atMost(SEND_TIMEOUT).untilAsserted(() ->
                verify(passwordResetMailSender, times(2)).send(anyString(), anyString(), any(Duration.class)));
    }

    @Test
    @DisplayName("메일은 발급이 커밋된 뒤 요청 스레드가 아닌 전용 스레드에서 보낸다.")
    void 커밋된_뒤_전용_스레드에서_메일을_보낸다() {
        saveUser();
        AtomicReference<String> sendThread = new AtomicReference<>();
        AtomicReference<Integer> committedCount = new AtomicReference<>();
        willAnswer(invocation -> {
            sendThread.set(Thread.currentThread().getName());
            // 트랜잭션 밖의 다른 커넥션으로 조회한다. 커밋 전이면 0건이 보인다.
            committedCount.set(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM temporary_passwords WHERE status = 'ACTIVE'", Integer.class));
            return null;
        }).given(passwordResetMailSender).send(anyString(), anyString(), any(Duration.class));

        String requestThread = Thread.currentThread().getName();
        passwordResetService.resetPassword(EMAIL);

        // 메일 Mock 안에서 스레드 이름을 먼저 기록하고 DB 조회는 그다음에 한다. 두 값이 모두 채워질 때까지 기다린다.
        await().atMost(SEND_TIMEOUT).untilAsserted(() -> {
            assertThat(sendThread.get()).isNotNull();
            assertThat(committedCount.get()).isEqualTo(1);
        });
        assertThat(sendThread.get())
                .isNotEqualTo(requestThread)
                .startsWith("password-reset-mail-");
    }

    @Test
    @DisplayName("메일을 끝내 보내지 못해도 요청은 성공하고, 발급된 임시 비밀번호는 폐기된다.")
    void 메일을_끝내_보내지_못하면_발급을_폐기한다() {
        UUID userId = saveUser();
        willThrow(new MailSendException("SMTP 연결 실패"))
                .given(passwordResetMailSender).send(anyString(), anyString(), any(Duration.class));

        // 메일은 커밋 뒤에 보내므로 발송 실패가 요청 결과를 바꾸지 않는다.
        assertThatCode(() -> passwordResetService.resetPassword(EMAIL)).doesNotThrowAnyException();

        // 최초 1번 + 재시도 2번(1초, 2초 대기)이 끝나면 폐기된다.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(temporaryPasswordRepository.findAll())
                        .singleElement()
                        .extracting(TemporaryPassword::getStatus)
                        .isEqualTo(TemporaryPasswordStatus.DISCARDED));
        assertThat(temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE))
                .isEmpty();
        verify(passwordResetMailSender, times(3)).send(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("1분 안에 다시 요청하면 거부하고 기존 임시 비밀번호는 그대로 둔다.")
    void 일분_안에_다시_요청하면_거부한다() {
        UUID userId = saveUser();
        passwordResetService.resetPassword(EMAIL);

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(PasswordResetTooManyRequestsException.class);

        // 거부된 요청은 이전 것을 파기하지도, 새로 발급하지도 않는다.
        assertThat(temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE))
                .hasSize(1);
        await().atMost(SEND_TIMEOUT).untilAsserted(() ->
                verify(passwordResetMailSender, times(1)).send(anyString(), anyString(), any(Duration.class)));
    }

    @Test
    @DisplayName("같은 계정의 동시 요청은 하나만 발급하고 나머지는 거부한다.")
    void 같은_계정의_동시_요청은_하나만_발급한다() throws ExecutionException, InterruptedException {
        UUID userId = saveUser();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2); // 두 스레드가 모두 준비될 때까지
        CountDownLatch start = new CountDownLatch(1); // 동시에 출발시키는 신호
        List<Future<Throwable>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    passwordResetService.resetPassword(EMAIL);
                    return null;
                } catch (Throwable throwable) {
                    return throwable; //예외를 결과로 돌려받아 메인 스레드에서 검증한다.
                }
            }));
        }
        ready.await();
        start.countDown();

        List<Throwable> errors = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            Throwable error = future.get();
            if (error != null) {
                errors.add(error);
            }
        }
        executor.shutdown();

        assertThat(errors).singleElement().isInstanceOf(PasswordResetTooManyRequestsException.class);
        assertThat(temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE))
                .hasSize(1);
        await().atMost(SEND_TIMEOUT).untilAsserted(() ->
                verify(passwordResetMailSender, times(1)).send(anyString(), anyString(), any(Duration.class)));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("메일 실행기가 작업을 받지 못하면 요청은 성공하고, 발급은 바로 폐기된다.")
    void 메일_실행기가_작업을_받지_못하면_발급을_바로_폐기한다() {
        UUID userId = saveUser();
        // 실행기를 멈춰 새 작업을 거부하게 만든다. 멈춘 실행기가 다른 테스트에 남지 않게 끝나면 컨텍스트를 다시 만든다.
        mailExecutor.shutdown();

        assertThatCode(() -> passwordResetService.resetPassword(EMAIL)).doesNotThrowAnyException();

        // 거부되면 요청 스레드에서 바로 폐기하므로 기다리지 않고 확인한다.
        assertThat(temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE))
                .isEmpty();
        assertThat(temporaryPasswordRepository.findAll())
                .singleElement()
                .extracting(TemporaryPassword::getStatus)
                .isEqualTo(TemporaryPasswordStatus.DISCARDED);
        verify(passwordResetMailSender, never()).send(anyString(), anyString(), any(Duration.class));
    }


    private UUID saveUser() {
        return new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .email(EMAIL)
                        .name("사용자")
                        .password("encoded-original-password")
                        .role(Role.USER)
                        .build()).getId());
    }

    private void moveIssuedAtToPast() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                jdbcTemplate.update(
                        "UPDATE temporary_passwords set created_at = created_at - interval '2 minutes'"
                ));
    }

}
