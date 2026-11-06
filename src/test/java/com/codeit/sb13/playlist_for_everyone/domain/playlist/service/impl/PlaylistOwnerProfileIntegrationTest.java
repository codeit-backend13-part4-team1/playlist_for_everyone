package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.docker.compose.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("플레이리스트 소유자 프로필 이미지 통합 테스트")
class PlaylistOwnerProfileIntegrationTest {

  private static final LocalDateTime FIXED_AT = LocalDateTime.of(2026, 10, 1, 12, 0);
  private static final int OWNER_COUNT = 10;

  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @MockitoBean
  private FileStorage fileStorage;

  @Autowired
  private PlaylistService playlistService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  private final List<UUID> userIds = new ArrayList<>();
  private final List<UUID> fileIds = new ArrayList<>();
  private final List<UUID> playlistIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    given(fileStorage.getFileUrl(org.mockito.ArgumentMatchers.anyString()))
        .willAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(0));
  }

  @AfterEach
  void cleanUp() {
    playlistIds.forEach(id -> jdbcTemplate.update("DELETE FROM playlists WHERE id = ?", id));
    userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    fileIds.forEach(id -> jdbcTemplate.update("DELETE FROM file_infos WHERE id = ?", id));
  }

  @Test
  @DisplayName("소유자가 서로 다른 플레이리스트 목록을 조회해도 프로필 이미지 때문에 쿼리가 늘어나지 않는다")
  void 목록_조회는_소유자_수와_관계없이_쿼리_수가_일정하다() {
    // given
    for (int i = 0; i < OWNER_COUNT; i++) {
      UUID ownerId = insertUserWithProfileImage("profile/owner-" + i + ".png");
      insertPlaylist(ownerId);
    }
    UUID requesterId = UUID.randomUUID();
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, null, null,
        OWNER_COUNT, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    assertThat(statistics.isStatisticsEnabled()).isTrue();
    long before = statistics.getPrepareStatementCount();

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    long statementCount = statistics.getPrepareStatementCount() - before;
    assertThat(response.data()).hasSize(OWNER_COUNT);
    assertThat(response.data()).allSatisfy(playlist ->
        assertThat(playlist.owner().profileImageUrl()).startsWith("https://cdn.test/profile/"));
    // 목록 조회 1회 + 전체 개수 1회 + 구독 여부 1회. 소유자 프로필 이미지는 추가 쿼리가 없어야 한다.
    assertThat(statementCount).isLessThanOrEqualTo(3L);
  }

  @Test
  @DisplayName("프로필 이미지가 없는 소유자의 플레이리스트는 이미지 URL이 null이다")
  void 프로필_이미지가_없는_소유자는_이미지_URL이_null이다() {
    // given
    UUID ownerId = insertUser(null);
    UUID playlistId = insertPlaylist(ownerId);

    // when
    PlaylistResponse response = playlistService.getPlaylist(playlistId, UUID.randomUUID());

    // then
    assertThat(response.owner().profileImageUrl()).isNull();
  }

  @Test
  @DisplayName("프로필 이미지가 있는 소유자의 단건 조회 응답에는 이미지 URL이 담긴다")
  void 프로필_이미지가_있는_소유자의_단건_조회에는_이미지_URL이_담긴다() {
    // given
    UUID ownerId = insertUserWithProfileImage("profile/single.png");
    UUID playlistId = insertPlaylist(ownerId);

    // when
    PlaylistResponse response = playlistService.getPlaylist(playlistId, UUID.randomUUID());

    // then
    assertThat(response.owner().profileImageUrl()).isEqualTo("https://cdn.test/profile/single.png");
  }

  //-------[헬퍼 메서드]-----
  private UUID insertUserWithProfileImage(String fileKey) {
    UUID fileId = UUID.randomUUID();
    fileIds.add(fileId);
    jdbcTemplate.update(
        """
            INSERT INTO file_infos (id, file_key, original_filename, content_type, size, created_at)
            VALUES (?, ?, 'profile.png', 'image/png', 1024, CURRENT_TIMESTAMP)
            """,
        fileId, fileKey);
    return insertUser(fileId);
  }

  private UUID insertUser(UUID profileImageId) {
    UUID userId = UUID.randomUUID();
    userIds.add(userId);
    jdbcTemplate.update(
        """
            INSERT INTO users (id, email, nickname, role, enabled, created_at, profile_image_id)
            VALUES (?, ?, 'tester', 'USER', true, CURRENT_TIMESTAMP, ?)
            """,
        userId, userId + "@test.example", profileImageId);
    return userId;
  }

  private UUID insertPlaylist(UUID ownerId) {
    UUID playlistId = UUID.randomUUID();
    playlistIds.add(playlistId);
    jdbcTemplate.update(
        """
            INSERT INTO playlists
              (id, owner_id, title, description, subscriber_count, created_at, updated_at)
            VALUES (?, ?, '제목', '설명', 0, ?, ?)
            """,
        playlistId, ownerId, Timestamp.valueOf(FIXED_AT), Timestamp.valueOf(FIXED_AT));
    return playlistId;
  }
}