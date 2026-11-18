package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제 서비스와 PostgreSQL을 사용하여 동일 알림의 동시 읽음 처리를 검증한다.
 *
 * <p>첫 작업이 잠금을 획득한 뒤 트랜잭션 종료를 기다리게 하고, 두 번째 작업의 DB 잠금 대기를 확인한다.
 * 이후 첫 커밋을 허용하여 두 작업이 예외 없이 완료되고 알림이 삭제되는지 검증한다.
 * HTTP·JWT·CSRF 응답 검증은 별도 테스트의 역할이다.
 *
 * <p>테스트 전체에 @Transactional을 두지 않는다. 준비 데이터는 먼저 커밋하고,
 * 각 작업 스레드에서 TransactionTemplate으로 별도 트랜잭션을 시작한다.
 * 실제 커밋된 데이터는 작업 스레드 종료 후 알림 → 사용자 순서로 직접 정리한다.
 */
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("알림 읽음 처리 통합 테스트")
public class NotificationServiceIntegrationTest {

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
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID receiverId = UUID.randomUUID();

    private final UUID notificationId = UUID.randomUUID();

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp(){
        transactionTemplate = new TransactionTemplate(transactionManager);

        // 콜백 종료 시 커밋하여 두 작업 스레드의 트랜잭션에서 같은 알림을 조회할 수 있게 한다.
        transactionTemplate.executeWithoutResult(status ->{
            jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
            """, receiverId, receiverId + "@test.example", "알림 수신자");

            jdbcTemplate.update("""
                INSERT INTO notifications (id, receiver_id, title, content, level, created_at)
                VALUES (?, ?, '알림 제목', '알림 내용', 'INFO', CURRENT_TIMESTAMP)
            """, notificationId, receiverId);
        });
    }

    @AfterEach
    void cleanUp(){
        // 각 테스트의 finally에서 작업 종료를 기다린 뒤, 이 사례가 만든 데이터만 정리한다.
        transactionTemplate.executeWithoutResult(status ->{
            jdbcTemplate.update("DELETE FROM notifications WHERE receiver_id = ?", receiverId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", receiverId);
        });
    }

    @SneakyThrows
    @Test
    @DisplayName("동일 알림을 동시에 읽음 처리하면 잠금 대기 후 두 요청 모두 정상 완료된다.")
    void confirmWaitsForLockAndCompletesBothDeletions(){
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstDeletionPrepared = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CompletableFuture<Integer> secondBackendPid = new CompletableFuture<>();

        try {
            Future<?> firstDeletion = startFirstDeletion(executor, firstDeletionPrepared, allowFirstCommit);

            // 잠금 획득과 삭제 요청까지 진행됐다는 신호이며, 첫 트랜잭션은 아직 커밋하지 않았다.
            assertThat(firstDeletionPrepared.await(10, TimeUnit.SECONDS))
                    .as("첫 번째 삭제 작업의 잠금 획득")
                    .isTrue();

            Future<?> secondDeletion = startSecondDeletion(executor, secondBackendPid);

            Integer backendPid = secondBackendPid.get(5, TimeUnit.SECONDS);

            // DB가 실제로 잠금 획득을 기다리는 상태인지 별도 연결에서 반복 확인한다.
            await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(()->{
                        Boolean waitingForLock = jdbcTemplate.queryForObject("""
                                    SELECT cardinality(pg_blocking_pids(?)) > 0
                                """, Boolean.class, backendPid);

                        assertThat(waitingForLock)
                                .as("두 번째 삭제 작업의 DB 잠금 대기")
                                .isTrue();
                    });

            allowFirstCommit.countDown();
            // 두 트랜잭션의 종료를 기다린다. 작업 중 발생한 예외도 get()을 통해 테스트 실패로 드러난다.
            firstDeletion.get(10, TimeUnit.SECONDS);
            secondDeletion.get(10, TimeUnit.SECONDS);

            Long remainingCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM notifications WHERE id = ?
                    """, Long.class, notificationId);

            assertThat(remainingCount).isZero();
        } finally {
            // 검증 도중 실패해도 첫 작업의 대기를 풀고, 데이터 정리 전에 작업 스레드 종료를 기다린다.
            allowFirstCommit.countDown();
            executor.shutdown();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)){
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                        .as("삭제 작업 스레드 종료")
                        .isTrue();
            }
        }


    }


    /**
     * 첫 삭제 작업은 confirm이 반환된 뒤에도 바깥 트랜잭션을 유지하여 잠금을 잡아 둔다.
     * 커밋 허용 신호를 받으면 콜백이 끝나고, TransactionTemplate이 커밋을 수행한다.
     */
    private Future<?> startFirstDeletion(ExecutorService executor,
                                         CountDownLatch firstDeletionPrepared,
                                         CountDownLatch allowFirstCommit){
        return executor.submit(()->{
            transactionTemplate.executeWithoutResult(status ->{
                notificationService.confirm(notificationId, receiverId);
                firstDeletionPrepared.countDown();
                try{
                    assertThat(allowFirstCommit.await(30, TimeUnit.SECONDS))
                            .as("첫 번째 삭제 트랜잭션의 커밋 허용 신호")
                            .isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("첫 번째 삭제 작업의 대기가 중단됐습니다.",e);
                }
            });
        });
    }

    /**
     * 두 번째 작업의 DB 연결을 식별할 PID를 테스트 스레드에 전달한 뒤 같은 알림의 읽음 처리를 요청한다.
     * secondBackendPid는 PID 전달용이며, 반환하는 Future가 삭제 작업 전체의 완료·실패를 나타낸다.
     */
    private Future<?> startSecondDeletion(ExecutorService executor,
                                          CompletableFuture<Integer> secondBackendPid){
        return executor.submit(()->{
            transactionTemplate.executeWithoutResult(status -> {
                Integer backendPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                secondBackendPid.complete(backendPid);
                notificationService.confirm(notificationId, receiverId);
            });
        });
    }


}
