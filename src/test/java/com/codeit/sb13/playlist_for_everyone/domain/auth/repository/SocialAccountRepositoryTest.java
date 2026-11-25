package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;


import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialAccount;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL에 Flyway를 적용해 소셜 계정 테이블과 제약조건을 검증한다.
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL 소셜 계정 저장소")
class SocialAccountRepositoryTest {

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
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("제공자와 회원 ID로 연결된 사용자를 조회한다.")
    void 제공자와_회원ID로_연결된_사용자를_조회한다() {
        User user = saveUser("google@gmail.com");

        socialAccountRepository.saveAndFlush(SocialAccount.connect(user, SocialProvider.GOOGLE, "google-sub-1"));
        entityManager.clear();

        Optional<SocialAccount> found =
                socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub-1");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("연결되지 않은 회원 ID는 조회되지 않는다.")
    void 연결되지_않은_회원ID는_조회되지_않는다() {
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "unknown"))
                .isEmpty();
    }

    @Test
    @DisplayName("다른 제공자라면 같은 회원 ID도 각각 저장한다.")
    void 다른_제공자라면_같은_회원ID도_각각_저장한다() {
        socialAccountRepository.saveAndFlush(
                SocialAccount.connect(saveUser("a@example.com"), SocialProvider.GOOGLE, "12345"));
        socialAccountRepository.saveAndFlush(
                SocialAccount.connect(saveUser("b@example.com"), SocialProvider.KAKAO, "12345"));

        assertThat(socialAccountRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 제공자의 같은 회원 ID는 중복 저장할 수 없다.")
    void 같은_제공자의_같은_회원ID는_중복_저장할_수_없다() {
        socialAccountRepository.saveAndFlush(
                SocialAccount.connect(saveUser("first@example.com"), SocialProvider.KAKAO, "12345"));

        assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
                SocialAccount.connect(saveUser("second@example.com"), SocialProvider.KAKAO, "12345")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 사용자에게 같은 제공자를 두 번 연결할 수 없다.")
    void 한_사용자에게_같은_제공자를_두_번_연결할_수_없다() {
        User user = saveUser("same@example.com");
        socialAccountRepository.saveAndFlush(SocialAccount.connect(user, SocialProvider.GOOGLE, "sub-1"));

        assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
                SocialAccount.connect(user, SocialProvider.GOOGLE, "sub-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .name("소셜 사용자")
                .password(null)
                .role(Role.USER)
                .build());
    }

}
