package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * 엔티티가 Flyway로 만든 temporary_passwords 테이블과 맞는지, 상태별 조회가 되는지 검증한다.
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
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Testcontainers
@DisplayName("PostgreSQL 임시 비밀번호 저장소")
class TemporaryPasswordRepositoryTest {

    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 11, 11, 18, 3);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private TemporaryPasswordRepository temporaryPasswordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("저장한 임시 비밀번호를 다시 조회할 수 있다.")
    void 저장한_임시_비밀번호를_다시_조회할_수_있다() {
        UUID userId = saveUser("a@test.com");
        TemporaryPassword saved = temporaryPasswordRepository.saveAndFlush(
                TemporaryPassword.issue(userId, "encoded", EXPIRED_AT));
        em.clear();

        TemporaryPassword found = temporaryPasswordRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getPassword()).isEqualTo("encoded");
        assertThat(found.getExpiredAt()).isEqualTo(EXPIRED_AT);
        assertThat(found.getStatus()).isEqualTo(TemporaryPasswordStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
    }


    @Test
    @DisplayName("사용자의 사용 가능한 임시 비밀번호만 조회한다.")
    void 사용자의_사용_가능한_임시_비밀번호만_조회한다() {
        UUID userId = saveUser("a@test.com");
        UUID otherUserId = saveUser("b@test.com");

        TemporaryPassword active = temporaryPasswordRepository.save(
                TemporaryPassword.issue(userId, "active", EXPIRED_AT)
        );
        TemporaryPassword disabled = temporaryPasswordRepository.save(
                TemporaryPassword.issue(userId, "disabled", EXPIRED_AT)
        );

        disabled.discard();

        temporaryPasswordRepository.save(disabled);
        temporaryPasswordRepository.save(TemporaryPassword.issue(otherUserId, "other", EXPIRED_AT));
        em.flush();
        em.clear();

        List<TemporaryPassword> result = temporaryPasswordRepository.findAllByUserIdAndStatus(userId, TemporaryPasswordStatus.ACTIVE);

        assertThat(result).extracting(TemporaryPassword::getId).containsExactly(active.getId());
    }

    private UUID saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .name("n")
                .role(Role.USER)
                .build()).getId();
    }

}
