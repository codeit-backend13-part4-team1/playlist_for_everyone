package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListSubscriptionDuplicatedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListSubscriptionNotFoundException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.docker.compose.enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("플레이리스트 구독 통합 테스트")
class PlaylistSubscriptionIntegrationTest {

  private static final LocalDateTime FIXED_UPDATED_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

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
  private PlaylistService playlistService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private final UUID ownerId = UUID.randomUUID();
  private final UUID playlistId = UUID.randomUUID();
  private final List<UUID> userIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    insertUser(ownerId);
    insertPlaylist(playlistId, ownerId);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM subscriptions WHERE playlist_id = ?", playlistId);
    jdbcTemplate.update("DELETE FROM playlists WHERE id = ?", playlistId);
    userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
  }

  @Test
  @DisplayName("구독하면 구독 정보가 저장되고 조회 응답에 구독 여부와 구독자 수가 반영되며 수정일은 바뀌지 않는다")
  void 구독하면_조회_응답에_구독_여부와_구독자_수가_반영된다() {
    // given
    UUID subscriberId = newUser();

    // when
    playlistService.subscribe(playlistId, subscriberId);

    // then
    PlaylistResponse response = playlistService.getPlaylist(playlistId, subscriberId);
    assertThat(response.subscribedByMe()).isTrue();
    assertThat(response.subscriberCount()).isEqualTo(1L);
    assertThat(subscriptionRows()).isEqualTo(1L);
    assertThat(subscriberCountColumn()).isEqualTo(1L);
    assertThat(updatedAtColumn()).isEqualTo(FIXED_UPDATED_AT);
  }

  @Test
  @DisplayName("구독을 취소하면 구독 정보가 삭제되고 구독 여부와 구독자 수가 되돌아간다")
  void 구독을_취소하면_구독_여부와_구독자_수가_되돌아간다() {
    // given
    UUID subscriberId = newUser();
    playlistService.subscribe(playlistId, subscriberId);

    // when
    playlistService.unsubscribe(playlistId, subscriberId);

    // then
    PlaylistResponse response = playlistService.getPlaylist(playlistId, subscriberId);
    assertThat(response.subscribedByMe()).isFalse();
    assertThat(response.subscriberCount()).isZero();
    assertThat(subscriptionRows()).isZero();
  }

  @Test
  @DisplayName("구독과 취소를 반복해도 중복·미구독 요청은 거부되고 구독자 수는 구독 정보 개수와 같다")
  void 반복_요청에도_구독자_수는_구독_정보_개수와_같다() {
    // given
    UUID subscriberId = newUser();

    // when & then
    playlistService.subscribe(playlistId, subscriberId);
    assertThrowsDuplicated(subscriberId);
    assertThat(subscriberCountColumn()).isEqualTo(1L);

    playlistService.unsubscribe(playlistId, subscriberId);
    assertThrowsNotSubscribed(subscriberId);
    assertThat(subscriberCountColumn()).isZero();

    playlistService.subscribe(playlistId, subscriberId);
    assertThat(subscriberCountColumn()).isEqualTo(subscriptionRows());
    assertThat(subscriptionRows()).isEqualTo(1L);
  }

  @Test
  @DisplayName("서로 다른 사용자가 동시에 구독해도 구독자 수는 구독 정보 개수와 같다")
  void 서로_다른_사용자의_동시_구독에도_구독자_수가_일치한다() throws Exception {
    // given
    int threads = 8;
    List<Callable<Throwable>> tasks = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      UUID subscriberId = newUser();
      tasks.add(() -> run(() -> playlistService.subscribe(playlistId, subscriberId)));
    }

    // when
    List<Throwable> results = runConcurrently(tasks);

    // then
    assertThat(results).containsOnlyNulls();
    assertThat(subscriptionRows()).isEqualTo(threads);
    assertThat(subscriberCountColumn()).isEqualTo(threads);
  }

  @Test
  @DisplayName("같은 사용자가 동시에 구독하면 한 건만 성공하고 나머지는 중복 구독 예외이며 구독자 수는 1이다")
  void 같은_사용자의_동시_구독은_한_건만_성공한다() throws Exception {
    // given
    UUID subscriberId = newUser();
    int threads = 4;
    List<Callable<Throwable>> tasks = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      tasks.add(() -> run(() -> playlistService.subscribe(playlistId, subscriberId)));
    }

    // when
    List<Throwable> results = runConcurrently(tasks);

    // then
    assertThat(results.stream().filter(r -> r == null).count()).isEqualTo(1L);
    assertThat(results.stream().filter(r -> r != null))
        .allSatisfy(r -> assertThat(r).isInstanceOf(PlayListSubscriptionDuplicatedException.class));
    assertThat(subscriptionRows()).isEqualTo(1L);
    assertThat(subscriberCountColumn()).isEqualTo(1L);
  }

  //-------[헬퍼 메서드]-----
  private Throwable run(Runnable action) {
    try {
      action.run();
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  private List<Throwable> runConcurrently(List<Callable<Throwable>> tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Throwable>> futures = new ArrayList<>();
      for (Callable<Throwable> task : tasks) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
          }
          return task.call();
        }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS))
          .as("모든 요청이 실행 준비를 마쳐야 한다.").isTrue();
      start.countDown();

      List<Throwable> results = new ArrayList<>();
      for (Future<Throwable> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
          .as("테스트 작업 스레드가 종료되어야 한다.").isTrue();
    }
  }

  private void assertThrowsDuplicated(UUID subscriberId) {
    assertThat(run(() -> playlistService.subscribe(playlistId, subscriberId)))
        .isInstanceOf(PlayListSubscriptionDuplicatedException.class);
  }

  private void assertThrowsNotSubscribed(UUID subscriberId) {
    assertThat(run(() -> playlistService.unsubscribe(playlistId, subscriberId)))
        .isInstanceOf(PlayListSubscriptionNotFoundException.class);
  }

  private UUID newUser() {
    UUID id = UUID.randomUUID();
    insertUser(id);
    return id;
  }

  private void insertUser(UUID userId) {
    userIds.add(userId);
    jdbcTemplate.update(
        """
            INSERT INTO users (id, email, nickname, role, enabled, created_at)
            VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
            """,
        userId, userId + "@test.example", "tester");
  }

  private void insertPlaylist(UUID id, UUID owner) {
    jdbcTemplate.update(
        """
            INSERT INTO playlists
              (id, owner_id, title, description, subscriber_count, created_at, updated_at)
            VALUES (?, ?, '제목', '설명', 0, ?, ?)
            """,
        id, owner, Timestamp.valueOf(FIXED_UPDATED_AT), Timestamp.valueOf(FIXED_UPDATED_AT));
  }

  private long subscriptionRows() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM subscriptions WHERE playlist_id = ?", Long.class, playlistId);
  }

  private long subscriberCountColumn() {
    return jdbcTemplate.queryForObject(
        "SELECT subscriber_count FROM playlists WHERE id = ?", Long.class, playlistId);
  }

  private LocalDateTime updatedAtColumn() {
    return jdbcTemplate.queryForObject(
            "SELECT updated_at FROM playlists WHERE id = ?", Timestamp.class, playlistId)
        .toLocalDateTime();
  }
}
