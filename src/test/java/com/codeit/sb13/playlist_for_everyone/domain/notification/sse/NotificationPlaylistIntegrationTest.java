package com.codeit.sb13.playlist_for_everyone.domain.notification.sse;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationTargetReader;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 실제 플레이리스트 생성·콘텐츠 추가 서비스부터 알림 저장과 SSE 발행 요청까지 검증한다.
 * PostgreSQL·Flyway와 실제 Spring 빈을 사용하며, SseEventPublisher는 Mock으로 대체한다.
 * Redis 전송이나 브라우저 수신 완료는 검증 범위에 포함하지 않는다.
 *
 * <p>수신자 조회 컴포넌트와 알림 저장소의 Spy는 기본적으로 실제 구현을 실행한다.
 * 조회·저장 실패 사례에서만 예외를 유도하여 업무 커밋 유지와 후속 수신자 처리를 확인한다.
 *
 * <p>테스트 전체를 @Transactional로 감싸지 않는다. 커밋·롤백 시점을 확인하는 사례는
 * TransactionTemplate으로 업무 경계를 제어하고, 실패 처리 사례는 서비스 자체의 트랜잭션을 사용한다.
 * 알림은 별도의 REQUIRES_NEW 트랜잭션으로 저장된다.
 *
 * <p>ApplicationEvents에는 롤백된 업무의 발행 기록도 남는다. 이벤트 기록과 함께
 * 알림 생성 요청·DB 저장·SSE 발행 요청 여부를 확인하여 커밋 후 소비 여부를 구분한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@RecordApplicationEvents
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("플레이리스트 업무 이벤트 기반 알림 통합 테스트")
public class NotificationPlaylistIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void
    configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @MockitoBean
    private SseEventPublisher sseEventPublisher;

    @MockitoSpyBean
    private NotificationTargetReader targetReader;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID firstReceiverId = UUID.randomUUID();
    private final UUID secondReceiverId = UUID.randomUUID();
    private final UUID existingPlaylistId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();


    @BeforeEach
    void setUp(){
        transactionTemplate = new TransactionTemplate(transactionManager);

        // 알림 저장의 독립 트랜잭션에서도 수신자를 조회할 수 있도록 사용자 준비를 먼저 커밋한다.
        transactionTemplate.executeWithoutResult(status ->{
            insertUser(ownerId, "홍길동");
            insertUser(firstReceiverId, "첫 번째 수신자");
            insertUser(secondReceiverId, "두 번째 수신자");
        });
    }

    @AfterEach
    void cleanUp(){
        transactionTemplate.executeWithoutResult(status ->{
            jdbcTemplate.update("""
                  DELETE FROM notifications
                  WHERE receiver_id IN (?, ?, ?)
                  """, ownerId, firstReceiverId, secondReceiverId);

            jdbcTemplate.update("""
                  DELETE FROM follows
                  WHERE followee_id = ?
                  """, ownerId);

            jdbcTemplate.update("""
                  DELETE FROM playlist_contents
                  WHERE playlist_id = ?
                  """, existingPlaylistId);

            jdbcTemplate.update("""
                  DELETE FROM subscriptions
                  WHERE playlist_id = ?
                  """, existingPlaylistId);

            jdbcTemplate.update("""
                  DELETE FROM playlists
                  WHERE owner_id = ?
                  """, ownerId);

            jdbcTemplate.update("""
                  DELETE FROM contents
                  WHERE id = ?
                  """, contentId);

            jdbcTemplate.update("""
                  DELETE FROM users
                  WHERE id IN (?, ?, ?)
                  """, ownerId, firstReceiverId, secondReceiverId);
        });
    }

    @Test
    @DisplayName("실제 플레이리스트 생성 커밋 후 처리 시점의 팔로워들에게 알림을 저장하고 SSE 발행을 요청한다.")
    void savesNotificationsAfterActualPlaylistCreationCommits(ApplicationEvents applicationEvents) {
        insertFollow(firstReceiverId, ownerId);

        UUID playlistId = transactionTemplate.execute(status ->{
            PlaylistResponse response = playlistService.create(ownerId, new PlaylistCreateRequest("퇴근 후 영화", "함께 볼 영화"));
            assertThat(applicationEvents.stream(PlaylistCreatedEvent.class))
                    .hasSize(1);

            // 이벤트 발생 후 관계를 추가하여 수신자를 커밋 후 처리 시점에 조회하는지 확인한다.
            insertFollow(secondReceiverId, ownerId);
            assertThat(notificationRepository.count()).isZero();
            verifyNoInteractions(sseEventPublisher);

            return response.id();
        });

        Long playlistCount = countPlaylistsById(playlistId);

        assertThat(playlistCount).isEqualTo(1L);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(firstReceiverId, secondReceiverId);

        ArgumentCaptor<SseEvent<?>> sseCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher, times(2)).publish(sseCaptor.capture());
        assertThat(sseCaptor.getAllValues())
                .extracting(SseEvent::receiverId)
                .containsExactlyInAnyOrder(firstReceiverId, secondReceiverId);
    }

    @Test
    @WithMockUser
    @DisplayName("실제 콘텐츠 추가 커밋 후 처리 시점의 구독자들에게 알림을 저장하고 SSE 발행을 요청한다.")
    void savesNotificationsAfterActualPlaylistContentAdditionCommits(ApplicationEvents applicationEvents) {
        insertPlaylist();
        insertContent();
        insertSubscription(firstReceiverId);

        transactionTemplate.executeWithoutResult(status ->{
            playlistService.addContent(existingPlaylistId, contentId, ownerId);
            assertThat(applicationEvents.stream(PlaylistContentAddedEvent.class))
                    .hasSize(1);

            // 이벤트에 수신자를 고정하지 않고, 처리 시점의 구독 관계를 사용하는지 확인한다.
            insertSubscription(secondReceiverId);
            assertThat(notificationRepository.count()).isZero();
            verifyNoInteractions(sseEventPublisher);
        });

        assertThat(countPlaylistContents(existingPlaylistId, contentId))
                .isEqualTo(1L);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(firstReceiverId, secondReceiverId);

        ArgumentCaptor<SseEvent<?>> sseCaptor = ArgumentCaptor.captor();

        verify(sseEventPublisher, times(2)).publish(sseCaptor.capture());
        assertThat(sseCaptor.getAllValues())
                .extracting(SseEvent::receiverId)
                .containsExactlyInAnyOrder(firstReceiverId, secondReceiverId);
    }

    @Test
    @DisplayName("실제 플레이리스트 생성이 롤백되면 플레이리스트와 알림을 저장하지 않고 SSE를 발행하지 않는다.")
    void doesNotSaveNotificationsWhenActualPlaylistCreationRollsBack(ApplicationEvents applicationEvents){
        insertFollow(firstReceiverId, ownerId);
        insertFollow(secondReceiverId, ownerId);

        UUID playlistId = transactionTemplate.execute(status ->{
            PlaylistResponse response = playlistService.create(ownerId, new PlaylistCreateRequest("퇴근 후 영화", "함께 볼 영화"));
            status.setRollbackOnly();
            return response.id();
        });

        assertThat(countPlaylistsById(playlistId)).isZero();
        assertThat(applicationEvents.stream(PlaylistCreatedEvent.class))
                .containsExactly(new PlaylistCreatedEvent(playlistId, "퇴근 후 영화", ownerId, "홍길동"));
        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .isEmpty();
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
    }

    @Test
    @WithMockUser
    @DisplayName("실제 콘텐츠 추가가 롤백되면 콘텐츠 연결과 알림을 저장하지 않고 SSE를 발행하지 않는다.")
    void doesNotSaveNotificationsWhenActualPlaylistContentAdditionRollsBack(ApplicationEvents applicationEvents){
        insertPlaylist();
        insertContent();
        insertSubscription(firstReceiverId);
        insertSubscription(secondReceiverId);

        transactionTemplate.executeWithoutResult(status ->{
            playlistService.addContent(existingPlaylistId, contentId, ownerId);
            status.setRollbackOnly();
        });

        assertThat(countPlaylistsById(existingPlaylistId)).isEqualTo(1L);
        assertThat(countPlaylistContents(existingPlaylistId, contentId)).isZero();
        assertThat(applicationEvents.stream(PlaylistContentAddedEvent.class))
                .containsExactly(new PlaylistContentAddedEvent(existingPlaylistId, "퇴근 후 영화", contentId, "인셉션", ownerId));
        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .isEmpty();
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
    }

    @Test
    @DisplayName("팔로워 조회가 실패해도 생성된 플레이리스트를 유지하고 알림 없이 실패를 기록한다.")
    void keepsPlaylistWhenFollowerLookupFails(ApplicationEvents applicationEvents, CapturedOutput output){
        insertFollow(firstReceiverId, ownerId);
        insertFollow(secondReceiverId, ownerId);

        doThrow(new IllegalStateException("테스트용 팔로워 조회 실패"))
                .when(targetReader)
                .findFollowerIds(ownerId);

        PlaylistResponse response = playlistService.create(ownerId,
                new PlaylistCreateRequest("퇴근 후 영화", "함께 볼 영화"));

        verify(targetReader).findFollowerIds(ownerId);
        assertThat(countPlaylistsById(response.id())).isEqualTo(1L);
        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .isEmpty();
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
        assertThat(output.getAll())
                .contains(
                        "playlistId=" + response.id(),
                        "ownerId=" + ownerId,
                        "exceptionType=IllegalStateException"
                );
    }

    @Test
    @WithMockUser
    @DisplayName("구독자 조회가 실패해도 추가된 콘텐츠 연결을 유지하고 알림 없이 실패를 기록한다.")
    void keepsPlaylistContentWhenSubscriberLookupFails(ApplicationEvents applicationEvents, CapturedOutput output){
        insertPlaylist();
        insertContent();
        insertSubscription(firstReceiverId);
        insertSubscription(secondReceiverId);

        doThrow(new IllegalStateException("테스트용 구독자 조회 실패"))
                .when(targetReader)
                .findSubscriberIds(existingPlaylistId);

        playlistService.addContent(existingPlaylistId, contentId, ownerId);

        verify(targetReader).findSubscriberIds(existingPlaylistId);
        assertThat(countPlaylistContents(existingPlaylistId, contentId)).isEqualTo(1L);
        assertThat(applicationEvents.stream(NotificationCreateRequestedEvent.class))
                .isEmpty();
        assertThat(notificationRepository.count()).isZero();
        verifyNoInteractions(sseEventPublisher);
        assertThat(output.getAll())
                .contains(
                        "playlistId=" + existingPlaylistId,
                        "contentId=" + contentId,
                        "actorId=" + ownerId,
                        "exceptionType=IllegalStateException"
                );
    }

    @Test
    @DisplayName("플레이리스트 생성 알림의 첫 저장이 실패해도 업무 커밋을 유지하고 후속 정상 알림만 저장하여 SSE를 발행한다.")
    void continuesSavingPlaylistCreatedNotificationsAfterFirstSaveFailure(CapturedOutput output){
        insertFollow(firstReceiverId, ownerId);
        insertFollow(secondReceiverId, ownerId);
        failFirstNotificationSave();

        PlaylistResponse response = playlistService.create(ownerId, new PlaylistCreateRequest("퇴근 후 영화", "함께 볼 영화"));
        assertThat(countPlaylistsById(response.id())).isEqualTo(1L);
        ArgumentCaptor<Notification> saveCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saveCaptor.capture());

        assertThat(saveCaptor.getAllValues())
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(firstReceiverId, secondReceiverId);

        // 조회 순서를 가정하지 않고, 실제 저장 호출 순서로 실패·성공 수신자를 구분한다.
        UUID failedReceiverId = saveCaptor.getAllValues().get(0).getReceiverId();
        UUID successfulReceiverId = saveCaptor.getAllValues().get(1).getReceiverId();

        assertThat(notificationRepository.findAll())
                .extracting(Notification::getReceiverId)
                .containsExactly(successfulReceiverId);

        ArgumentCaptor<SseEvent<?>> sseCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(sseCaptor.capture());
        assertThat(sseCaptor.getValue().receiverId())
                .isEqualTo(successfulReceiverId);
        assertThat(output.getAll())
                .contains("알림 저장 실패. receiverId=" + failedReceiverId,
                        DataIntegrityViolationException.class.getSimpleName());

    }

    @Test
    @WithMockUser
    @DisplayName("콘텐츠 추가 알림의 첫 저장이 실패해도 업무 커밋을 유지하고 후속 정상 알림만 저장하여 SSE를 발행한다.")
    void continuesSavingPlaylistContentAddedNotificationAfterFirstSaveFailure(CapturedOutput output){
        insertPlaylist();
        insertContent();
        insertSubscription(firstReceiverId);
        insertSubscription(secondReceiverId);
        failFirstNotificationSave();

        playlistService.addContent(existingPlaylistId, contentId, ownerId);

        assertThat(countPlaylistContents(existingPlaylistId, contentId)).isEqualTo(1L);

        ArgumentCaptor<Notification> saveCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saveCaptor.capture());
        assertThat(saveCaptor.getAllValues())
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(firstReceiverId, secondReceiverId);

        // 조회 순서를 가정하지 않고, 실제 저장 호출 순서로 실패·성공 수신자를 구분한다.
        UUID failedReceiverId = saveCaptor.getAllValues().get(0).getReceiverId();
        UUID successfulReceiverId = saveCaptor.getAllValues().get(1).getReceiverId();
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getReceiverId)
                .containsExactly(successfulReceiverId);

        ArgumentCaptor<SseEvent<?>> sseCaptor = ArgumentCaptor.captor();
        verify(sseEventPublisher).publish(sseCaptor.capture());
        assertThat(sseCaptor.getValue().receiverId())
                .isEqualTo(successfulReceiverId);
        assertThat(output.getAll())
                .contains(
                        "알림 저장 실패. receiverId=" + failedReceiverId,
                        DataIntegrityViolationException.class.getSimpleName()
                );
    }

    private Long countPlaylistsById(UUID playlistId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM playlists WHERE id = ?",
                Long.class, playlistId);
    }

    private void insertUser(UUID userId, String name)
    {
        jdbcTemplate.update("""
              INSERT INTO users (
                  id, email, nickname, role,
                  enabled, created_at
              )
              VALUES (?, ?, ?, 'USER', true,
              CURRENT_TIMESTAMP)
              """,
                userId,
                userId + "@test.example",
                name
        );
    }

    private void insertFollow(UUID followerId, UUID
            followeeId) {
        jdbcTemplate.update("""
              INSERT INTO follows (
                  id, follower_id, followee_id,
                  created_at
              )
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """,
                UUID.randomUUID(),
                followerId,
                followeeId
        );
    }

    private void insertPlaylist() {
        jdbcTemplate.update("""
              INSERT INTO playlists (
                  id, owner_id, title, description,
                  subscriber_count, created_at,
                  updated_at
              )
              VALUES (
                  ?, ?, '퇴근 후 영화', '함께 볼 영화',
                  0, CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP
              )
              """,
                existingPlaylistId,
                ownerId
        );
    }

    private void insertContent() {
        jdbcTemplate.update("""
              INSERT INTO contents (
                  id, type, title, description,
                  thumbnail_url,
                  provider, provider_item_id,
                  created_at
              )
              VALUES (
                  ?, 'movie', '인셉션', '테스트 콘텐츠',
                  'https://example.com/thumb.png',
                  'ADMIN', ?, CURRENT_TIMESTAMP
              )
              """,
                contentId,
                contentId.toString()
        );
    }

    private void insertSubscription(UUID subscriberId) {
        jdbcTemplate.update("""
              INSERT INTO subscriptions (
                  id, playlist_id, subscriber_id,
                  created_at
              )
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """,
                UUID.randomUUID(),
                existingPlaylistId,
                subscriberId
        );
    }

    private Long countPlaylistContents(UUID playlistId, UUID contentId) {
        return jdbcTemplate.queryForObject("""
              SELECT COUNT(*)
              FROM playlist_contents
              WHERE playlist_id = ? AND content_id
              = ?
              """,
                Long.class,
                playlistId,
                contentId
        );
    }

    /**
     * 저장 경계의 첫 호출만 실패시키고, 이후 호출은 실제 저장소로 위임한다.
     * Spy로 저장 예외를 유도하는 테스트이며 DB 제약 위반 자체를 재현하는 것은 아니다.
     */
    private void failFirstNotificationSave() {
        // Spring Data 저장소 프록시로 위임하는 Spy의 기본 응답을 보존한다.
        Answer<?> defaultAnswer =
                mockingDetails(notificationRepository)
                        .getMockCreationSettings()
                        .getDefaultAnswer();

        doThrow(new
                DataIntegrityViolationException("테스트용 알림 저장 실패"))
                .doAnswer(defaultAnswer)
                .when(notificationRepository)
                .save(any(Notification.class));
    }
}
