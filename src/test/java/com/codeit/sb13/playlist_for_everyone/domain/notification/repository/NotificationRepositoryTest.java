package com.codeit.sb13.playlist_for_everyone.domain.notification.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL·Flyway·Querydsl로 수신자별 목록·전체 개수와 커서 탐색을 확인한다.
 *
 * <p>생성시각이 같은 행의 ID 순서, 커서 대상 삭제 후 탐색, 삭제 후 목록·개수 변화를 검증한다.
 * 이때 deleteById와 flush는 삭제된 상태를 준비하기 위한 테스트 코드다.
 * 같은 삭제 사례에서 수신자 사용자가 보존되는지도 확인한다.
 * 미존재 알림의 잠금 조회가 빈 Optional을 반환하는지도 확인한다.
 *
 * <p>DTO 변환 테스트는 수신자가 초기화되지 않은 상태에서 변환 전후 SQL 수를 비교한다.
 * Hibernate 통계는 이 테스트 클래스에서만 활성화하며, 서비스 호출 없이 저장소·매핑 동작을 검증한다.
 *
 * <p>기본 DataJpaTest 트랜잭션이 각 사례의 변경을 롤백한다. 소유자별 삭제 여부는 서비스 테스트,
 * 실제 잠금 대기와 커밋 결과는 NotificationServiceIntegrationTest, HTTP 응답은 JWT 통합 테스트에서 검증한다.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("알림 저장소 통합 테스트")
public class NotificationRepositoryTest {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("지정한 수신자의 알림만 목록과 전체 개수에 포함한다.")
    void searchesAndCountsOnlyReceiverNotifications(){
        UUID receiverId = createReceiver();
        UUID otherReceiverId = createReceiver();

        Notification mine = notificationRepository.saveAndFlush(new Notification(new NotificationCommand(
                receiverId,
                "내 알림",
                "내 알림 내용",
                NotificationLevel.INFO
        ), findReceiver(receiverId)));

        notificationRepository.saveAndFlush(new Notification(new NotificationCommand(
                otherReceiverId,
                "다른 사용자 알림",
                "다른 알림 내용",
                NotificationLevel.INFO
        ), findReceiver(otherReceiverId)));

        NotificationSearchCondition condition = new NotificationSearchCondition(
                null,
                null,
                20,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> result = notificationRepository.search(condition);
        assertThat(result).extracting(Notification::getId)
                        .containsExactly(mine.getId());

        assertThat(notificationRepository.searchCount(condition)).isEqualTo(1L);

    }

    @Test
    @DisplayName("생성 시각 내림차순으로 limit보다 1개 많은 알림까지만 조회한다.")
    void searchesLimitPlusOneNotificationsInDescendingOrder(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID secondId = insertNotification(receiverId, createdAt.minusMinutes(1));
        insertNotification(receiverId, createdAt.minusMinutes(3));
        UUID firstId = insertNotification(receiverId, createdAt);
        UUID thirdId = insertNotification(receiverId, createdAt.minusMinutes(2));

        NotificationSearchCondition condition = new NotificationSearchCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> result = notificationRepository.search(condition);

        assertThat(result)
                .extracting(Notification::getId)
                .containsExactly(firstId, secondId, thirdId);

        assertThat(notificationRepository.searchCount(condition))
                .isEqualTo(4L);
    }

    @Test
    @DisplayName("생성 시각 오름차순으로 limit보다 1개 많은 알림까지만 조회한다.")
    void searchesLimitPlusOneNotificationsInAscendingOrder(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID secondId = insertNotification(receiverId, createdAt.plusMinutes(1));
        insertNotification(receiverId, createdAt.plusMinutes(3));
        UUID firstId = insertNotification(receiverId, createdAt);
        UUID thirdId = insertNotification(receiverId, createdAt.plusMinutes(2));

        NotificationSearchCondition condition = new NotificationSearchCondition(
                null,
                null,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> result = notificationRepository.search(condition);

        assertThat(result)
                .extracting(Notification::getId)
                .containsExactly(firstId, secondId, thirdId);

        assertThat(notificationRepository.searchCount(condition))
                .isEqualTo(4L);
    }

    @Test
    @DisplayName("내림차순에서는 커서보다 이전시각의 본인 알림만 조회하고 전체 개수를 유지한다.")
    void searchesOnlyNotificationsAfterCursorInDescendingOrder(){
        UUID receiverId = createReceiver();
        UUID otherReceiverId = createReceiver();
        LocalDateTime cursorTime = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID oldestId = insertNotification(receiverId, cursorTime.minusMinutes(2));
        insertNotification(receiverId, cursorTime.plusMinutes(1));
        UUID cursorId = insertNotification(receiverId, cursorTime);
        UUID nextId = insertNotification(receiverId, cursorTime.minusMinutes(1));
        insertNotification(otherReceiverId, cursorTime.minusMinutes(1));

        NotificationSearchCondition condition = new NotificationSearchCondition(
                cursorTime.toString(),
                cursorId,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> result = notificationRepository.search(condition);

        assertThat(result)
                .extracting(Notification::getId)
                .containsExactly(nextId, oldestId);

        assertThat(notificationRepository.searchCount(condition))
                .isEqualTo(4L);

        NotificationSearchCondition endCondition = new NotificationSearchCondition(
                cursorTime.minusMinutes(2)
                        .toString(),
                oldestId,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        assertThat(notificationRepository.search(endCondition)).isEmpty();
        assertThat(notificationRepository.searchCount(endCondition)).isEqualTo(4L);
    }

    @Test
    @DisplayName("오름차순에서는 커서 이후 시각의 본인 알림만 조회하고 전체 개수를 유지한다.")
    void searchesOnlyNotificationsAfterCursorInAscendingOrder(){
        UUID receiverId = createReceiver();
        UUID otherReceiverId = createReceiver();
        LocalDateTime cursorTime = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID newestId = insertNotification(receiverId, cursorTime.plusMinutes(2));
        insertNotification(receiverId,cursorTime.minusMinutes(1));
        UUID cursorId = insertNotification(receiverId, cursorTime);
        UUID nextId = insertNotification(receiverId, cursorTime.plusMinutes(1));

        insertNotification(otherReceiverId, cursorTime.plusMinutes(1));

        NotificationSearchCondition condition = new NotificationSearchCondition(
                cursorTime.toString(),
                cursorId,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> result = notificationRepository.search(condition);
        assertThat(result)
                .extracting(Notification::getId)
                .containsExactly(nextId, newestId);

        assertThat(notificationRepository.searchCount(condition))
                .isEqualTo(4L);

        NotificationSearchCondition endCondition = new NotificationSearchCondition(
                cursorTime.plusMinutes(2).toString(),
                newestId,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        assertThat(notificationRepository.search(endCondition)).isEmpty();
        assertThat(notificationRepository.searchCount(endCondition)).isEqualTo(4L);
    }

    @Test
    @DisplayName("생성 시각이 같아도 ID내림차순으로 중복·누락 없이 다음 페이지를 조회한다.")
    void paginatesEqualCreatedAtIdDescending(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID id4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        insertNotification(id2, receiverId, createdAt);
        insertNotification(id4, receiverId, createdAt);
        insertNotification(id1, receiverId, createdAt);
        insertNotification(id3, receiverId, createdAt);

        NotificationSearchCondition firstCondition = new NotificationSearchCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> firstBatch = notificationRepository.search(firstCondition);
        assertThat(firstBatch)
                .extracting(Notification::getId)
                .containsExactly(id4,id3,id2);

        NotificationSearchCondition nextCondition = new NotificationSearchCondition(
                createdAt.toString(),
                id3,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> nextBatch = notificationRepository.search(nextCondition);

        assertThat(nextBatch)
                .extracting(Notification::getId)
                .containsExactly(id2, id1);

        assertThat(notificationRepository.searchCount(nextCondition)).isEqualTo(4L);


        NotificationSearchCondition endCondition = new NotificationSearchCondition(
                createdAt.toString(),
                id1,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        assertThat(notificationRepository.search(endCondition)).isEmpty();
    }

    @Test
    @DisplayName("생성 시각이 같아도 ID 오름차순으로 중복·누락 없이 다음 페이지를 조회한다.")
    void paginatesEqualCreatedAtIdAscending(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID id4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        insertNotification(id2, receiverId, createdAt);
        insertNotification(id4, receiverId, createdAt);
        insertNotification(id1, receiverId, createdAt);
        insertNotification(id3, receiverId, createdAt);

        NotificationSearchCondition firstCondition = new NotificationSearchCondition(
                null,
                null,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> firstBatch = notificationRepository.search(firstCondition);
        assertThat(firstBatch)
                .extracting(Notification::getId)
                .containsExactly(id1,id2,id3);

        NotificationSearchCondition nextCondition = new NotificationSearchCondition(
                createdAt.toString(),
                id2,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );
        List<Notification> nextBatch = notificationRepository.search(nextCondition);
        assertThat(nextBatch)
                .extracting(Notification::getId)
                .containsExactly(id3, id4);

        assertThat(notificationRepository.searchCount(nextCondition)).isEqualTo(4L);
        NotificationSearchCondition endCondition = new NotificationSearchCondition(
                createdAt.toString(),
                id4,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        assertThat(notificationRepository.search(endCondition)).isEmpty();
    }


    @Test
    @DisplayName("기준 알림을 삭제해도 저장한커서로 내림차순 다음 페이지를 조회한다.")
    void continuesDescendingPaginationAfterCursorNotificationIsDeleted(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID id4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        insertNotification(id2, receiverId, createdAt);
        insertNotification(id4, receiverId, createdAt);
        insertNotification(id1, receiverId, createdAt);
        insertNotification(id3, receiverId, createdAt);

        NotificationSearchCondition firstCondition = new NotificationSearchCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> firstBatch = notificationRepository.search(firstCondition);

        assertThat(firstBatch)
                .extracting(Notification::getId)
                .containsExactly(id4, id3, id2);

        Notification lastReturned = firstBatch.get(1);
        String cursor = lastReturned.getCreatedAt().toString();
        UUID idAfter = lastReturned.getId();

        notificationRepository.deleteById(idAfter);
        notificationRepository.flush();

        assertThat(notificationRepository.existsById(idAfter)).isFalse();

        NotificationSearchCondition nextCondition = new NotificationSearchCondition(
                cursor,
                idAfter,
                2,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> nextBatch = notificationRepository.search(nextCondition);
        assertThat(nextBatch)
                .extracting(Notification::getId)
                .containsExactly(id2, id1);

        assertThat(notificationRepository.searchCount(nextCondition)).isEqualTo(3L);

    }

    @Test
    @DisplayName("기준 알림을 삭제해도 저장한 커서로 오름차순 다음 페이지를 조회한다.")
    void continuesAscendingPaginationAfterCursorNotificationIsDeleted(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID id4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        insertNotification(id2, receiverId, createdAt);
        insertNotification(id4, receiverId, createdAt);
        insertNotification(id1, receiverId, createdAt);
        insertNotification(id3, receiverId, createdAt);

        NotificationSearchCondition firstCondition = new NotificationSearchCondition(
                null,
                null,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> firstBatch = notificationRepository.search(firstCondition);
        assertThat(firstBatch)
                .extracting(Notification::getId)
                .containsExactly(id1, id2, id3);

        Notification lastReturned = firstBatch.get(1);
        String cursor = lastReturned.getCreatedAt().toString();
        UUID idAfter = lastReturned.getId();

        notificationRepository.deleteById(idAfter);
        notificationRepository.flush();

        assertThat(notificationRepository.existsById(idAfter)).isFalse();

        NotificationSearchCondition nextCondition = new NotificationSearchCondition(
                cursor,
                idAfter,
                2,
                SortDirection.ASCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> nextBatch = notificationRepository.search(nextCondition);
        assertThat(nextBatch)
                .extracting(Notification::getId)
                .containsExactly(id3, id4);

        assertThat(notificationRepository.searchCount(nextCondition)).isEqualTo(3L);
    }

    @Test
    @DisplayName("알림 삭제 후 수신자별 목록과 전체 개수가 변경되고 사용자 정보는 유지된다.")
    void reflectsDeletionInReceiverSearchResultsAndCount(){
        UUID receiverId = createReceiver();
        UUID otherReceiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID targetId = insertNotification(receiverId, createdAt);
        UUID remainingId = insertNotification(receiverId, createdAt.minusMinutes(1));

        UUID otherId = insertNotification(otherReceiverId, createdAt);

        NotificationSearchCondition condition = new NotificationSearchCondition(
                null,
                null,
                20,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        NotificationSearchCondition otherCondition = new NotificationSearchCondition(
                null,
                null,
                20,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                otherReceiverId
        );

        assertThat(notificationRepository.searchCount(condition)).isEqualTo(2L);

        notificationRepository.deleteById(targetId);
        notificationRepository.flush();

        assertThat(notificationRepository.existsById(targetId)).isFalse();

        Long receiverCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM users WHERE id = ?
        """, Long.class, receiverId);

        assertThat(receiverCount).isEqualTo(1L);

        assertThat(notificationRepository.search(condition))
                .extracting(Notification::getId)
                .containsExactly(remainingId);

        assertThat(notificationRepository.searchCount(condition)).isEqualTo(1L);

        assertThat(notificationRepository.search(otherCondition))
                .extracting(Notification::getId)
                .containsExactly(otherId);

        assertThat(notificationRepository.searchCount(otherCondition)).isEqualTo(1L);

    }

    @Test
    @DisplayName("limit이 정수 최댓값이어도 오버플로 없이 알림을 조회한다.")
    void searchesNotificationsWithMaximumIntegerLimit(){
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        UUID firstId = insertNotification(receiverId, createdAt);
        UUID secondId = insertNotification(receiverId, createdAt.minusMinutes(1));

        NotificationSearchCondition condition = new NotificationSearchCondition(
                null,
                null,
                Integer.MAX_VALUE,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        List<Notification> result = notificationRepository.search(condition);
        assertThat(result)
                .extracting(Notification::getId)
                .containsExactly(firstId, secondId);
    }

    @Test
    @DisplayName("존재하지 않는 알림 ID로 잠금 조회하면 빈 Optional을 반환한다.")
    void returnsEmptyWhenPessimisticLockTargetDoesNotExist(){

        UUID missingId = UUID.randomUUID();
        assertThat(notificationRepository.findWithPessimisticLockById(missingId))
                .isEmpty();
    }

    @Test
    @DisplayName("알림을 DTO로 변환해도 수신자 조회 SQL이 추가로 발생하지 않는다.")
    void convertsNotificationsToDtosWithoutAdditionalUserQueries(){
        // JDBC로 준비하여 User를 영속성 컨텍스트에 미리 올리지 않는다. 사전 로딩이 추가 조회를 가릴 수 있다.
        UUID receiverId = createReceiver();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 5, 10, 0);
        insertNotification(receiverId, createdAt);
        insertNotification(receiverId, createdAt.minusMinutes(1));

        NotificationSearchCondition condition = new NotificationSearchCondition(null, null, 20, SortDirection.DESCENDING, SortBy.CREATED_AT, receiverId);

        List<Notification> notifications = notificationRepository.search(condition);

        assertThat(notifications).hasSize(2);

        // 실제 미초기화 프록시에서 ID만 읽는 상황인지 확인한 뒤 DTO 변환을 측정한다.
        assertThat(notifications).allSatisfy(notification ->
                assertThat(Hibernate.isInitialized(notification.getReceiver())).isFalse()
        );

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();

        // 통계 비활성화로 카운터가 계속 0인 경우를 성공으로 오판하지 않도록 한다.
        assertThat(statistics.isStatisticsEnabled()).isTrue();

        // SessionFactory 전체의 누적값을 초기화하지 않고 변환 구간의 차이만 비교한다.
        // 같은 팩토리의 다른 SQL이 측정 중 끼어들지 않는 실행을 전제로 하며, 전체 조회의 SQL 수를 보장하지 않는다.
        long statementCountBefore = statistics.getPrepareStatementCount();
        List<NotificationDto> dtos = notifications.stream()
                .map(NotificationDto::from)
                .toList();

        assertThat(statistics.getPrepareStatementCount())
                .isEqualTo(statementCountBefore);

        assertThat(dtos)
                .extracting(NotificationDto::receiverId)
                .containsExactly(receiverId, receiverId);


    }


    private UUID createReceiver(){
        UUID receiverId = UUID.randomUUID();

        jdbcTemplate.update("""
        INSERT INTO users (id, email, nickname, role, enabled, created_at)
        VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
        """,
                receiverId,
                receiverId+"@test.example",
                "테스트 사용자"
        );

        return receiverId;
    }

    private UUID insertNotification(UUID receiverId, LocalDateTime createdAt){
        return insertNotification(UUID.randomUUID(),receiverId, createdAt);
    }

    private UUID insertNotification(UUID notificationId, UUID receiverId, LocalDateTime createdAt){
        jdbcTemplate.update("""
            INSERT INTO notifications(id, receiver_id, title, content, level, created_at)
            VALUES (?, ?, '알림 제목', '알림 내용', 'INFO', ?)
        """,
                notificationId,
                receiverId,
                createdAt);

        return notificationId;
    }

    private User findReceiver(UUID otherReceiverId) {
        return entityManager.find(User.class, otherReceiverId);
    }
}
