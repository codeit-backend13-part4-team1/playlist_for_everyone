package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.PasswordResetMailSender;
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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 실제 트랜잭션에서 발급·파기·롤백을 검증한다.
 * 테스트 트랜잭션을 끄고 서비스가 직접 커밋·롤백하게 한다. 메일 발송만 Mock으로 대체한다.
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
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("PostgreSQL 비밀번호 초기화 트랜잭션")
class PasswordResetServiceIntegrationTest {

    private static final String EMAIL = "reset@test.com";

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
    }

    @Test
    @DisplayName("메일 발송에 실패하면 임시 비밀번호 발급과 이전 것의 파기가 모두 취소된다.")
    void 메일_발송에_실패하면_모두_롤백된다() {
        UUID userId = saveUser();
        passwordResetService.resetPassword(EMAIL);   // 첫 발급은 성공
        moveIssuedAtToPast();
        TemporaryPassword first = temporaryPasswordRepository.findAll().get(0);

        willThrow(new MailSendException("SMTP 연결 실패"))
                .given(passwordResetMailSender).send(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(MailSendException.class);

        // 두 번째 발급은 저장되지 않았고, 첫 번째 것도 파기되지 않은 채 그대로 사용 가능하다.
        assertThat(temporaryPasswordRepository.findAll()).hasSize(1);
        assertThat(temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE))
                .extracting(TemporaryPassword::getId)
                .containsExactly(first.getId());
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
        verify(passwordResetMailSender, times(1)).send(anyString(), anyString(), any(Duration.class));
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
