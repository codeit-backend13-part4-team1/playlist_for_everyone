package com.codeit.sb13.playlist_for_everyone.domain.follow.service;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl.FollowSaver;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(FollowServiceIntegrationTest.MethodSecurityTestConfig.class)
@DisplayName("팔로우 서비스 통합 테스트")
public class FollowServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private FollowSaver followSaver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID followerId = UUID.randomUUID();
    private final UUID followeeId = UUID.randomUUID();
    private final UUID otherFollowerId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        insertUser(followerId, "팔로우하는 사용자");
        insertUser(followeeId, "팔로우받는 사용자");
        insertUser(otherFollowerId, "다른 요청자");
    }

    @AfterEach
    void cleanUp(){
        followRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", followerId, followeeId, otherFollowerId);
    }

    @Test
    @DisplayName("같은 팔로우를 다시 요청하면 기존 ID와 생성 시각을 유지하고 한건만 저장한다.")
    void follow_returnsExistingRelationForDuplicateRequest(){
        FollowCommand command = new FollowCommand(followeeId, followerId);

        FollowResult first = followService.follow(command);

        Follow before = followRepository.findById(first.followDto().id()).orElseThrow();
        FollowResult second = followService.follow(command);
        Follow after = followRepository.findById(second.followDto().id()).orElseThrow();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.followDto()).isEqualTo(first.followDto());

        assertThat(before.getCreatedAt()).isNotNull();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());

        assertThat(followRepository.count()).isEqualTo(1L);

    }

    @Test
    @WithMockUser
    @DisplayName("본인의 팔로우를 취소하면 DB에서 해당 관계가 삭제된다.")
    void unfollow_deletesOwnFollow(){
        FollowResult result = followService.follow(new FollowCommand(followeeId, followerId));

        UUID followId = result.followDto().id();
        assertThat(followRepository.existsById(followId)).isTrue();
        followService.unfollow(new UnFollowCommand(followId, followerId));

        assertThat(followRepository.findById(followId)).isEmpty();
        assertThat(followRepository.count()).isZero();

    }

    @Test
    @WithMockUser
    @DisplayName("같은 팔로우를 두 번 취소해도 예외 없이 삭제 상태를 유지한다.")
    void unfollow_allowsRepeatedCancellation(){
        FollowResult result = followService.follow(new FollowCommand(followeeId, followerId));

        UUID followId = result.followDto().id();
        UnFollowCommand command = new UnFollowCommand(followId, followerId);
        followService.unfollow(command);

        assertThat(followRepository.findById(followId)).isEmpty();

        assertThatCode(()-> followService.unfollow(command))
                .doesNotThrowAnyException();

        assertThat(followRepository.findById(followId)).isEmpty();
        assertThat(followRepository.count()).isZero();
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 팔로우를 취소해도 예외 없이 기존 관계를 유지한다.")
    void unfollow_ignoresMissingFollowAndPreservesExistingRelation(){
        FollowResult existing = followService.follow(new FollowCommand(followeeId, followerId));
        UUID existingFollowId = existing.followDto().id();
        UUID missingFollowId = UUID.randomUUID();

        assertThat(followRepository.existsById(missingFollowId)).isFalse();

        assertThatCode(() -> followService.unfollow(new UnFollowCommand(missingFollowId, followerId)))
                .doesNotThrowAnyException();

        assertThat(followRepository.findById(existingFollowId)).isPresent();

        assertThat(followRepository.count()).isEqualTo(1L);
    }

    @Test
    @WithMockUser
    @DisplayName("타인의 팔로우 취소 요청은 접근이 거부되고 기존 관계를 유지한다.")
    void unfollow_rejectsNonOwnerAndPreservesRelation(){
        FollowResult result = followService.follow(new FollowCommand(followeeId, followerId));
        UUID followId = result.followDto().id();

        UnFollowCommand command = new UnFollowCommand(followId, followeeId);

        assertThatThrownBy(()-> followService.unfollow(command))
                .isInstanceOf(AccessDeniedException.class);

        Follow preserved = followRepository.findById(followId).orElseThrow();

        assertThat(preserved.getFollowerId()).isEqualTo(followerId);
        assertThat(preserved.getFolloweeId()).isEqualTo(followeeId);
        assertThat(followRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 대상을 동시에 팔로우하면 한 건만 생성하고 같은 관계를 반환한다.")
    void follow_createsSingleRelationForConcurrentRequests() throws InterruptedException, ExecutionException, TimeoutException {
        FollowCommand command = new FollowCommand(followeeId, followerId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<FollowResult> request = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
            }
            return followService.follow(command);
        };

        try {
            Future<FollowResult> firstFuture = executor.submit(request);
            Future<FollowResult> secondFuture = executor.submit(request);

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("두 요청이 실행 준비를 마쳐야 한다.").isTrue();

            start.countDown();
            FollowResult first = firstFuture.get(20, TimeUnit.SECONDS);
            FollowResult second = secondFuture.get(20, TimeUnit.SECONDS);

            assertThat(List.of(first.created(), second.created()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(second.followDto()).isEqualTo(first.followDto());
            assertThat(followRepository.count()).isEqualTo(1L);
            assertThat(followRepository.existsById(first.followDto().id())).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }


    }

    @Test
    @DisplayName("실제 UNIQUE 충돌 후 실패한 저장 트랜잭션을 종료하고 기존 관계를 반환한다.")
    void follow_recoversAfterActualUniqueViolation(){
        UUID existingFollowId = UUID.randomUUID();
        LocalDateTime existingCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0);

        TransactionTemplate otherTransaction = new TransactionTemplate(transactionManager);
        otherTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        doAnswer(invocation -> {
            otherTransaction.executeWithoutResult(status -> jdbcTemplate.update(
                    """
                        INSERT INTO follows (id, follower_id, followee_id, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                        existingFollowId,
                        followerId,
                        followeeId,
                    Timestamp.valueOf(existingCreatedAt)
            ));

            return invocation.callRealMethod();
        }).when(followSaver).save(any(Follow.class));

        FollowResult result = followService.follow(new FollowCommand(followeeId, followerId));

        assertThat(result.created()).isFalse();
        assertThat(result.followDto().id()).isEqualTo(existingFollowId);
        assertThat(result.followDto().followeeId()).isEqualTo(followeeId);
        assertThat(result.followDto().followerId()).isEqualTo(followerId);

        Follow preserved = followRepository.findById(existingFollowId).orElseThrow();
        assertThat(preserved.getCreatedAt()).isEqualTo(existingCreatedAt);
        assertThat(followRepository.count()).isEqualTo(1L);

        verify(followSaver).save(any(Follow.class));


    }

    @Test
    @DisplayName("저장된 팔로우 조회하면 관계 ID와 두 사용자 ID를 반환한다.")
    void findFollowDto_returnsPersistedFollow(){
        FollowCommand command = new FollowCommand(followeeId, followerId);
        FollowResult created = followService.follow(command);

        FollowDto result = followService.findFollowDto(command);

        assertThat(result.id()).isEqualTo(created.followDto().id());
        assertThat(result.followeeId()).isEqualTo(followeeId);
        assertThat(result.followerId()).isEqualTo(followerId);


    }

    @Test
    @DisplayName("사용자는 존재하지만 팔로우 관계가 없으면 대상 ID를 담은 조회 실패 예외가 발생한다.")
    void findFollowDto_throwsWhenFollowMissing(){
        FollowCommand command = new FollowCommand(followeeId, followerId);
        assertThatThrownBy(()-> followService.findFollowDto(command))
                .isInstanceOfSatisfying(FollowNotFoundException.class,
                        e-> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.FOLLOW_NOT_FOUND);
                    assertThat(e.getDetails()).containsEntry("followeeId", followeeId);
                });


    }

    @Test
    @WithMockUser
    @DisplayName("팔로우를 취소한 뒤 조회하면 대상 ID를 담은 조회 실패 예외가 발생한다.")
    void findFollowDto_throwsAfterUnfollow(){
        FollowCommand command = new FollowCommand(followeeId, followerId);
        FollowResult created = followService.follow(command);

        assertThat(followService.findFollowDto(command).id())
                .isEqualTo(created.followDto().id());

        followService.unfollow(new UnFollowCommand(created.followDto().id(), followerId));

        assertThatThrownBy(()-> followService.findFollowDto(command))
                .isInstanceOfSatisfying(FollowNotFoundException.class
                        , e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.FOLLOW_NOT_FOUND);
                    assertThat(e.getDetails()).containsEntry("followeeId", followeeId);
                });


    }

    @Test
    @DisplayName("같은 대상을 조회해도 다른요청자의 팔로우 관계는 반환하지 않는다.")
    void findFollowDto_doesNotReturnAnotherUsersFollow(){
        FollowCommand ownerCommand = new FollowCommand(followeeId, followerId);
        FollowResult created = followService.follow(ownerCommand);

        assertThat(followService.findFollowDto(ownerCommand).id()).isEqualTo(created.followDto().id());

        FollowCommand otherCommand = new FollowCommand(followeeId, otherFollowerId);
        assertThatThrownBy(()-> followService.findFollowDto(otherCommand))
                .isInstanceOfSatisfying(FollowNotFoundException.class,
                        e-> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.FOLLOW_NOT_FOUND);
                    assertThat(e.getDetails()).containsEntry("followeeId", followeeId);
                });


    }

    @Test
    @DisplayName("존재하지 않는 대상 사용자 ID로 조회하면 해당 ID를 담은 조회 실패 예외가 발생한다.")
    void findFollowDto_throwsForNonexistentFollowee(){
        UUID missingFolloweeId = UUID.randomUUID();
        FollowCommand command = new FollowCommand(missingFolloweeId, followerId);

        assertThatThrownBy(()-> followService.findFollowDto(command))
                .isInstanceOfSatisfying(FollowNotFoundException.class,
                        e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.FOLLOW_NOT_FOUND);
                    assertThat(e.getDetails()).containsEntry("followeeId", missingFolloweeId);
                });


    }

    private void insertUser(UUID userId, String name){
        jdbcTemplate.update(
                """
                    INSERT INTO users (id, email, nickname, role, enabled, created_at)
                    VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                    """,
                userId,
                userId+"@test.example",
                name
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfig{

    }

}
