package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.parameters.P;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.docker.compose.enabled=false"
})
@Testcontainers
@RecordApplicationEvents
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("플레이리스트 생성 통합 테스트")
class PlaylistCreateIntegrationTest {

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

  @Autowired
  private ApplicationEvents applicationEvents;

  @Autowired
  private CommitEventCapture commitEventCapture;

  private final UUID ownerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    commitEventCapture.received.clear();
    jdbcTemplate.update(
        """
            INSERT INTO users (id, email, nickname, role, enabled, created_at)
            VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
            """,
        ownerId, ownerId + "@test.example", "tester");
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM playlists WHERE owner_id = ?", ownerId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", ownerId);
  }

  @Test
  @DisplayName("플레이리스트를 만들면 저장되고 생성 이벤트가 한 건 발행된다")
  void 플레이리스트를_만들면_저장되고_생성_이벤트가_한_건_발행된다() {
    // when
    PlaylistResponse response = playlistService.create(
        ownerId, new PlaylistCreateRequest("제목", "설명"));

    // then
    Long rows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM playlists WHERE id = ?", Long.class, response.id());
    assertThat(rows).isEqualTo(1L);

    List<PlaylistCreatedEvent> events =
        applicationEvents.stream(PlaylistCreatedEvent.class).toList();
    assertThat(events).hasSize(1);
    PlaylistCreatedEvent event = events.get(0);
    assertThat(event.playlistId()).isEqualTo(response.id());
    assertThat(event.playlistTitle()).isEqualTo("제목");
    assertThat(event.ownerId()).isEqualTo(ownerId);
    assertThat(event.ownerName()).isEqualTo("tester");
  }

  @Test
  @DisplayName("소유자가 없으면 예외가 발생하고 저장도 이벤트 발행도 하지 않는다")
  void 소유자가_없으면_저장도_이벤트_발행도_하지_않는다() {
    // given
    UUID unknownUserId = UUID.randomUUID();

    // when & then
    assertThatThrownBy(() -> playlistService.create(
        unknownUserId, new PlaylistCreateRequest("제목", "설명")))
        .isInstanceOf(UserNotFoundException.class);

    Long rows = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM playlists WHERE owner_id = ?", Long.class, unknownUserId);
    assertThat(rows).isZero();
    assertThat(applicationEvents.stream(PlaylistCreatedEvent.class).count()).isZero();
  }

  @Test
  @DisplayName("생성 이벤트는 트랜잭션 커밋 후 AFTER_COMMIT 리스너에 전달된다")
  void 생성_이벤트는_커밋_후_AFTER_COMMIT_리스너에_전달된다() {
    // when
    PlaylistResponse response = playlistService.create(
        ownerId, new PlaylistCreateRequest("제목", "설명"));

    // then
    assertThat(commitEventCapture.received).hasSize(1);
    assertThat(commitEventCapture.received.get(0).playlistId()).isEqualTo(response.id());
  }



  @TestConfiguration
  static class CommitEventCaptureConfig {

    @Bean
    CommitEventCapture commitEventCapture() {
      return new CommitEventCapture();
    }
  }

  static class CommitEventCapture {

    final List<PlaylistCreatedEvent> received = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PlaylistCreatedEvent event) {
      received.add(event);
    }
  }

  }