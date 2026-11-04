package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.batch.job.enabled=false",
        "everyone.admin.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL 관리자 초기화 통합 테스트")
class AdminInitializationServiceIntegrationTest {
    private static final String EMAIL = "admin-integration@example.com";
    private static final String NAME = "초기 관리자";
    private static final String PASSWORD = "AdminPassword123!";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("admin_init_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        // test 프로필의 H2 설정 대신 이 테스트 전용 PostgreSQL을 사용한다.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
    }

    @Autowired
    private AdminInitializationService adminInitializationService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        // 테스트 전용 DB에서 생성한 계정을 제거해 다음 테스트와 격리한다.
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("최초 초기화 시 ADMIN 계정과 암호화된 비밀번호를 저장한다")
    void 최초_실행하면_관리자를_저장한다() {
        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        User savedAdmin = userRepository.findByEmail(EMAIL).orElseThrow();

        assertThat(userRepository.count()).isEqualTo(1L);
        assertThat(savedAdmin.getId()).isNotNull();
        assertThat(savedAdmin.getEmail()).isEqualTo(EMAIL);
        assertThat(savedAdmin.getName()).isEqualTo(NAME);
        assertThat(savedAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedAdmin.isLocked()).isFalse();

        // 실제 인코더로 저장한 해시가 원문 비밀번호와 일치하는지 확인한다.
        assertThat(savedAdmin.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(
                PASSWORD,
                savedAdmin.getPassword()
        )).isTrue();
    }

    @Test
    @DisplayName("재실행해도 기존 관리자의 정보와 잠금 상태를 유지한다")
    void 재실행하면_기존_관리자를_유지한다() {
        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        User existingAdmin = userRepository.findByEmail(EMAIL).orElseThrow();
        existingAdmin.lock();
        userRepository.saveAndFlush(existingAdmin);

        // 설정값이 달라져도 기존 계정의 이름과 비밀번호를 덮어쓰지 않는다.
        adminInitializationService.initialize(
                EMAIL,
                "변경된 관리자 이름",
                "ChangedPassword123!"
        );

        User actual = userRepository.findByEmail(EMAIL).orElseThrow();

        assertThat(userRepository.count()).isEqualTo(1L);
        assertThat(actual.getId()).isEqualTo(existingAdmin.getId());
        assertThat(actual.getName()).isEqualTo(NAME);
        assertThat(actual.getPassword()).isEqualTo(existingAdmin.getPassword());
        assertThat(actual.getRole()).isEqualTo(Role.ADMIN);
        assertThat(actual.isLocked()).isTrue();
    }

    @Test
    @DisplayName("동일 이메일의 USER 계정이 있으면 정보를 변경하거나 승격하지 않는다")
    void 기존_USER_계정은_변경하지_않는다() {
        User existingUser = User.builder()
                .email(EMAIL)
                .name("기존 사용자")
                .password(passwordEncoder.encode("ExistingPassword123!"))
                .role(Role.USER)
                .build();

        existingUser.lock();
        User savedUser = userRepository.saveAndFlush(existingUser);

        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        User actual = userRepository.findByEmail(EMAIL).orElseThrow();

        assertThat(userRepository.count()).isEqualTo(1L);
        assertThat(actual.getId()).isEqualTo(savedUser.getId());
        assertThat(actual.getName()).isEqualTo(savedUser.getName());
        assertThat(actual.getPassword()).isEqualTo(savedUser.getPassword());
        assertThat(actual.getRole()).isEqualTo(Role.USER);
        assertThat(actual.isLocked()).isTrue();
    }

    @Test
    @DisplayName("동시에 초기화해도 두 호출 모두 정상 종료하고 관리자 한 명만 생성한다")
    void 동시_초기화하면_관리자_한_명만_생성한다() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);

        doAnswer(invocation -> {
            String encodedPassword = (String) invocation.callRealMethod();

            // 두 호출 모두 '계정 없음' 조회를 마친 상태에서 저장하도록 맞춘다.
            // 한 호출이 먼저 저장하고 다른 호출이 사전 조회에서 끝나는 것을 막는다.
            barrier.await(15, TimeUnit.SECONDS);

            return encodedPassword;
        }).when(passwordEncoder).encode(PASSWORD);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() ->
                    adminInitializationService.initialize(EMAIL, NAME, PASSWORD));

            Future<?> second = executor.submit(() ->
                    adminInitializationService.initialize(EMAIL, NAME, PASSWORD));

            // 작업 내부에서 예외가 전파되면 get()이 예외를 던져 테스트가 실패한다.
            // 따라서 두 호출 모두 트랜잭션 처리를 마치고 정상 반환해야 한다.
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertThat(userRepository.count()).isEqualTo(1L);

            User savedAdmin = userRepository.findByEmail(EMAIL).orElseThrow();

            assertThat(savedAdmin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(passwordEncoder.matches(
                    PASSWORD,
                    savedAdmin.getPassword()
            )).isTrue();
        } finally {
            // 실패하더라도 작업 스레드가 다음 테스트에 남지 않도록 종료한다.
            executor.shutdownNow();

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "관리자 초기화 테스트 작업이 종료되지 않았습니다."
                );
            }
        }
    }

}
