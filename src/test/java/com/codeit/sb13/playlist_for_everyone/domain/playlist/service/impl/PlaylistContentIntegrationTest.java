package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentDuplicatedException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
@DisplayName("플레이리스트 콘텐츠 추가 통합 테스트")
class PlaylistContentIntegrationTest {

  private static final LocalDateTime FIXED_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

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

  private final UUID ownerId = UUID.randomUUID();
  private final UUID playlistId = UUID.randomUUID();
  private final UUID contentId = UUID.randomUUID();
  private final List<UUID> userIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    insertUser(ownerId);
    insertPlaylist();
    insertContent();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("test-user", null, List.of()));
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbcTemplate.update("DELETE FROM playlist_contents WHERE playlist_id = ?", playlistId);
    jdbcTemplate.update("DELETE FROM playlists WHERE id = ?", playlistId);
    jdbcTemplate.update("DELETE FROM contents WHERE id = ?", contentId);
    userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
  }

  @Test
  @DisplayName("콘텐츠를 추가하면 연결이 저장되고 콘텐츠 추가 이벤트가 한 건 발행된다")
  void 콘텐츠를_추가하면_연결이_저장되고_이벤트가_한_건_발행된다() {
    // when
    playlistService.addContent(playlistId, contentId, ownerId);

    // then
    assertThat(playlistContentRows()).isEqualTo(1L);
    List<PlaylistContentAddedEvent> events =
        applicationEvents.stream(PlaylistContentAddedEvent.class).toList();
    assertThat(events).hasSize(1);
    PlaylistContentAddedEvent event = events.get(0);
    assertThat(event.playlistId()).isEqualTo(playlistId);
    assertThat(event.playlistTitle()).isEqualTo("제목");
    assertThat(event.contentId()).isEqualTo(contentId);
    assertThat(event.contentTitle()).isEqualTo("콘텐츠 제목");
    assertThat(event.actorId()).isEqualTo(ownerId);
  }

  @Test
  @DisplayName("이미 담긴 콘텐츠를 다시 추가하면 거부되고 이벤트가 추가로 발행되지 않는다")
  void 중복_추가가_거부되면_이벤트가_추가로_발행되지_않는다() {
    // given
    playlistService.addContent(playlistId, contentId, ownerId);

    // when & then
    assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, ownerId))
        .isInstanceOf(PlayListContentDuplicatedException.class);
    assertThat(applicationEvents.stream(PlaylistContentAddedEvent.class).count()).isEqualTo(1L);
  }

  @Test
  @DisplayName("소유자가 아니면 콘텐츠를 추가할 수 없고 이벤트도 발행되지 않는다")
  void 소유자가_아니면_추가할_수_없고_이벤트도_발행되지_않는다() {
    // given
    UUID otherUserId = UUID.randomUUID();
    insertUser(otherUserId);

    // when & then
    assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, otherUserId))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(playlistContentRows()).isZero();
    assertThat(applicationEvents.stream(PlaylistContentAddedEvent.class).count()).isZero();
  }

  //-------[헬퍼 메서드]-----
  private void insertUser(UUID userId) {
    userIds.add(userId);
    jdbcTemplate.update(
        """
            INSERT INTO users (id, email, nickname, role, enabled, created_at)
            VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
            """,
        userId, userId + "@test.example", "tester");
  }

  private void insertPlaylist() {
    jdbcTemplate.update(
        """
            INSERT INTO playlists
              (id, owner_id, title, description, subscriber_count, created_at, updated_at)
            VALUES (?, ?, '제목', '설명', 0, ?, ?)
            """,
        playlistId, ownerId, Timestamp.valueOf(FIXED_AT), Timestamp.valueOf(FIXED_AT));
  }

  private void insertContent() {
    jdbcTemplate.update(
        """
            INSERT INTO contents
              (id, type, title, description, thumbnail_url, provider, provider_item_id, created_at)
            VALUES (?, 'movie', '콘텐츠 제목', '설명', 'https://example.com/thumb.png',
                    'ADMIN', ?, CURRENT_TIMESTAMP)
            """,
        contentId, contentId.toString());
  }

  private long playlistContentRows() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM playlist_contents WHERE playlist_id = ?", Long.class, playlistId);
  }
}