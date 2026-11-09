package com.codeit.sb13.playlist_for_everyone.domain.user.service;


import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.batch.job.enabled=false",
        "everyone.admin.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(UserAdministrationServiceIntegrationTest.EventTestConfig.class)
@DisplayName("PostgreSQL 사용자 권한 및 계정 잠금 통합 테스트")
class UserAdministrationServiceIntegrationTest {


    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("user_administration_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", ()-> "org.postgresql.Driver");
    }

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CommittedRoleEventCollector eventCollector;

    @BeforeEach
    void setUp() {
        eventCollector.clear();
    }



    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("권한 변경을 DB에 저장하고 커밋 후 이벤트를 전달한다.")
    void 권한_변경을_저장하고_이벤트를_전달한다(Role newRole) {
        // ADMIN 승격괴 USER 강등을 모두 검증
        Role previousRole =
                newRole == Role.ADMIN ? Role.USER : Role.ADMIN;

        User savedUser = saveUser(previousRole, false);

        userAdministrationService.updateRole(savedUser.getId(), newRole);

        User actual = findUser(savedUser.getId());

        assertThat(actual.getRole()).isEqualTo(newRole);
        assertThat(actual.isLocked()).isFalse();
        assertThat(actual.getName()).isEqualTo(savedUser.getName());
        assertThat(actual.getPassword()).isEqualTo(savedUser.getPassword());

        assertThat(eventCollector.events()).containsExactly(
                new UserRoleChangedEvent(
                        savedUser.getId(),
                        previousRole,
                        newRole
                )
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("잠금 및 해제를 DB에 저장하고 권한 변경 이벤트는 발행하지 않는다")
    void 잠금_상태를_저장한다(boolean locked) {
        // 요청과 반대 상태로 시작하여 실제 변경이 일어나도록 한다.
        User savedUser = saveUser(Role.USER, !locked);

        userAdministrationService.updateLocked(savedUser.getId(), locked);

        User actual = findUser(savedUser.getId());

        assertThat(actual.isLocked()).isEqualTo(locked);
        assertThat(actual.getRole()).isEqualTo(Role.USER);
        assertThat(actual.getPassword()).isEqualTo(savedUser.getPassword());

        // 잠금 변경은 권한 변경 이벤트의 발행 대상이 아니다.
        assertThat(eventCollector.events()).isEmpty();
    }

    @Test
    @DisplayName("동일 권한으로 요청하면 권한 변경 이벤트를 전달하지 않는다")
    void 동일_권한이면_이벤트를_전달하지_않는다() {
        User savedUser = saveUser(Role.USER, false);

        userAdministrationService.updateRole(
                savedUser.getId(),
                Role.USER
        );

        assertThat(findUser(savedUser.getId()).getRole())
                .isEqualTo(Role.USER);
        assertThat(eventCollector.events()).isEmpty();
    }

    @Test
    @DisplayName("외부 트랜잭션이 커밋되기 전에는 이벤트 리스너를 실행하지 않는다")
    void 외부_트랜잭션_커밋_후에_이벤트를_전달한다() {
        User savedUser = saveUser(Role.USER, false);
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            // 서비스의 @Transactional은 현재 트랜잭션에 참여한다.
            userAdministrationService.updateRole(
                    savedUser.getId(),
                    Role.ADMIN
            );

            // UPDATE SQL을 실행해도 아직 커밋된 것은 아니다.
            userRepository.flush();

            assertThat(findUser(savedUser.getId()).getRole())
                    .isEqualTo(Role.ADMIN);

            // 이벤트는 이미 발행됐지만 AFTER_COMMIT 리스너는 대기한다.
            assertThat(eventCollector.events()).isEmpty();
        });

        // executeWithoutResult()가 정상 종료되면 커밋도 완료된다.
        assertThat(findUser(savedUser.getId()).getRole())
                .isEqualTo(Role.ADMIN);
        assertThat(eventCollector.events()).containsExactly(
                new UserRoleChangedEvent(
                        savedUser.getId(),
                        Role.USER,
                        Role.ADMIN
                )
        );
    }

    @Test
    @DisplayName("트랜잭션 실패 시 권한과 잠금을 롤백하고 이벤트를 전달하지 않는다")
    void 실패하면_변경을_롤백하고_이벤트를_전달하지_않는다() {
        User savedUser = saveUser(Role.USER, false);
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        IllegalStateException failure =
                new IllegalStateException("테스트용 후속 작업 실패");

        assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    // 두 변경 모두 하나의 외부 트랜잭션에 참여한다.
                    userAdministrationService.updateRole(
                            savedUser.getId(),
                            Role.ADMIN
                    );
                    userAdministrationService.updateLocked(
                            savedUser.getId(),
                            true
                    );

                    // 실제 UPDATE를 실행한 뒤 실패시켜 롤백을 검증한다.
                    userRepository.flush();

                    User changed = findUser(savedUser.getId());
                    assertThat(changed.getRole()).isEqualTo(Role.ADMIN);
                    assertThat(changed.isLocked()).isTrue();
                    assertThat(eventCollector.events()).isEmpty();

                    throw failure;
                })
        ).isSameAs(failure);

        // 실패한 트랜잭션이 끝난 후 새로 조회하여 원래 상태를 확인한다.
        User actual = findUser(savedUser.getId());

        assertThat(actual.getRole()).isEqualTo(Role.USER);
        assertThat(actual.isLocked()).isFalse();

        // 발행 자체를 취소하는 것이 아니라,
        // 롤백한 트랜잭션의 AFTER_COMMIT 처리가 실행되지 않는 것이다.
        assertThat(eventCollector.events()).isEmpty();
    }

    private User saveUser(Role role, boolean locked) {
        User user = User.builder()
                .email("administration-" + UUID.randomUUID() + "@example.com")
                .name("관리 대상 사용자")
                .password("test-encoded-password")
                .role(role)
                .build();

        if (locked) {
            user.lock();
        }

        return userRepository.saveAndFlush(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventTestConfig {
        @Bean
        CommittedRoleEventCollector committedRoleEventCollector() {
            return new CommittedRoleEventCollector();
        }
    }

    static class CommittedRoleEventCollector {
        private final List<UserRoleChangedEvent> events =
                new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void collect(UserRoleChangedEvent event) {
            events.add(event);
        }

        public List<UserRoleChangedEvent> events() {
            return List.copyOf(events);
        }

        public void clear() {
            events.clear();
        }
    }










}
