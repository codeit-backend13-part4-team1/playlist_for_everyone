package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistContentSummary;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
@DisplayName("플레이리스트 응답 콘텐츠 목록 통합 테스트")
class PlaylistContentsIntegrationTest {

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
  private final List<UUID> contentIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    given(fileStorage.getFileUrl(org.mockito.ArgumentMatchers.anyString()))
        .willAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(0));
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    contentIds.forEach(id -> jdbcTemplate.update("DELETE FROM reviews WHERE content_id = ?", id));
    playlistIds.forEach(
        id -> jdbcTemplate.update("DELETE FROM playlist_contents WHERE playlist_id = ?", id));
    contentIds.forEach(id -> jdbcTemplate.update("DELETE FROM content_tags WHERE content_id = ?", id));
    contentIds.forEach(id -> jdbcTemplate.update("DELETE FROM contents WHERE id = ?", id));
    playlistIds.forEach(id -> jdbcTemplate.update("DELETE FROM playlists WHERE id = ?", id));
    userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
    fileIds.forEach(id -> jdbcTemplate.update("DELETE FROM file_infos WHERE id = ?", id));
  }

  @Test
  @DisplayName("단건 조회는 콘텐츠를 플레이리스트에 추가한 순서대로 담고 평점·태그·썸네일 규칙을 따른다")
  void 단건_조회는_콘텐츠를_추가한_순서대로_담는다() {
    // given
    UUID ownerId = insertUser();
    UUID playlistId = insertPlaylist(ownerId);

    UUID fileId = insertFile("thumb/a.png");
    UUID withFile = insertContent("파일 콘텐츠", "https://old.test/a.png", fileId);
    insertTag(withFile, "액션");
    insertTag(withFile, "SF");
    insertReview(withFile, 4.0);
    insertReview(withFile, 5.0);

    UUID withoutFile = insertContent("링크 콘텐츠", "https://img.test/b.png", null);

    // 추가 시각은 withoutFile이 먼저, withFile이 나중이다 (INSERT 순서와 반대).
    linkContent(playlistId, withoutFile, 0);
    linkContent(playlistId, withFile, 1);

    // when
    PlaylistResponse response = playlistService.getPlaylist(playlistId, UUID.randomUUID());

    // then
    assertThat(response.contents()).extracting(PlaylistContentSummary::id)
        .containsExactly(withoutFile, withFile);

    PlaylistContentSummary first = response.contents().get(0);
    assertThat(first.title()).isEqualTo("링크 콘텐츠");
    assertThat(first.type()).isEqualTo(ContentType.movie);
    assertThat(first.thumbnailUrl()).isEqualTo("https://img.test/b.png");
    assertThat(first.tags()).isEmpty();
    assertThat(first.averageRating()).isEqualTo(0.0);
    assertThat(first.reviewCount()).isZero();

    PlaylistContentSummary second = response.contents().get(1);
    assertThat(second.title()).isEqualTo("파일 콘텐츠");
    // 파일이 있으면 기존 thumbnail_url이 아니라 파일 Key로 만든 URL을 쓴다.
    assertThat(second.thumbnailUrl()).isEqualTo("https://cdn.test/thumb/a.png");
    assertThat(second.tags()).containsExactlyInAnyOrder("액션", "SF");
    assertThat(second.averageRating()).isEqualTo(4.5);
    assertThat(second.reviewCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("콘텐츠가 없는 플레이리스트의 contents는 null이 아니라 빈 배열이다")
  void 콘텐츠가_없는_플레이리스트는_빈_배열이다() {
    // given
    UUID playlistId = insertPlaylist(insertUser());

    // when
    PlaylistResponse response = playlistService.getPlaylist(playlistId, UUID.randomUUID());

    // then
    assertThat(response.contents()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("수정 응답에도 콘텐츠 목록이 담기고 수정 내용이 DB에 반영된다")
  void 수정_응답에도_콘텐츠_목록이_담긴다() {
    // given
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("test-user", null, List.of()));
    UUID ownerId = insertUser();
    UUID playlistId = insertPlaylist(ownerId);
    UUID contentId = insertContent("콘텐츠", "https://img.test/c.png", null);
    insertTag(contentId, "태그");
    linkContent(playlistId, contentId, 0);

    // when
    PlaylistResponse response = playlistService.updatePlaylist(
        playlistId, new PlaylistUpdateRequest("새 제목", null), ownerId);

    // then
    assertThat(response.title()).isEqualTo("새 제목");
    assertThat(response.description()).isEqualTo("설명");
    assertThat(response.contents()).extracting(PlaylistContentSummary::id)
        .containsExactly(contentId);
    assertThat(response.contents().get(0).tags()).containsExactly("태그");

    String savedTitle = jdbcTemplate.queryForObject(
        "SELECT title FROM playlists WHERE id = ?", String.class, playlistId);
    assertThat(savedTitle).isEqualTo("새 제목");
  }

  @Test
  @DisplayName("목록 조회의 쿼리 수는 플레이리스트와 콘텐츠가 늘어도 일정하다")
  void 목록_조회의_쿼리_수는_플레이리스트_수와_관계없이_일정하다() {
    // given
    UUID ownerId = insertUser();
    for (int i = 0; i < 2; i++) {
      createPlaylistWithContents(ownerId, 2);
    }

    // when: 플레이리스트 2개
    StatementResult small = searchWithStatementCount(10);

    for (int i = 0; i < 8; i++) {
      createPlaylistWithContents(ownerId, 2);
    }

    // when: 플레이리스트 10개
    StatementResult large = searchWithStatementCount(10);

    // then
    assertThat(small.response().data()).hasSize(2);
    assertThat(large.response().data()).hasSize(10);
    assertThat(large.response().data()).allSatisfy(playlist ->
        assertThat(playlist.contents()).hasSize(2));
    assertThat(large.statementCount()).isEqualTo(small.statementCount());
  }

  @Test
  @DisplayName("서로 다른 콘텐츠가 배치 크기(100)를 넘어도 목록 조회의 쿼리 수는 늘어나지 않는다")
  void 콘텐츠가_배치_크기를_넘어도_쿼리_수가_일정하다() {
    // given
    UUID ownerId = insertUser();
    createPlaylistWithContents(ownerId, 1);

    // when: 콘텐츠 1개
    StatementResult small = searchWithStatementCount(10);

    createPlaylistWithContents(ownerId, 101);

    // when: 서로 다른 콘텐츠 102개 (배치 크기 100 초과)
    StatementResult large = searchWithStatementCount(10);

    // then
    assertThat(large.response().data()).hasSize(2);
    assertThat(large.statementCount()).isEqualTo(small.statementCount());
  }

  //-------[헬퍼 메서드]-----
  private record StatementResult(CursorResponse<PlaylistResponse> response, long statementCount) {
  }

  private StatementResult searchWithStatementCount(int limit) {
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, null, null,
        limit, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    assertThat(statistics.isStatisticsEnabled()).isTrue();
    long before = statistics.getPrepareStatementCount();

    CursorResponse<PlaylistResponse> response =
        playlistService.getPlaylists(request, UUID.randomUUID());

    return new StatementResult(response, statistics.getPrepareStatementCount() - before);
  }

  private void createPlaylistWithContents(UUID ownerId, int contentCount) {
    UUID playlistId = insertPlaylist(ownerId);
    for (int i = 0; i < contentCount; i++) {
      UUID contentId = insertContent("콘텐츠-" + UUID.randomUUID(), "https://img.test/x.png", null);
      insertTag(contentId, "태그");
      insertReview(contentId, 3.0);
      linkContent(playlistId, contentId, i);
    }
  }

  private UUID insertFile(String fileKey) {
    UUID fileId = UUID.randomUUID();
    fileIds.add(fileId);
    jdbcTemplate.update(
        """
            INSERT INTO file_infos (id, file_key, original_filename, content_type, size, created_at)
            VALUES (?, ?, 'thumb.png', 'image/png', 1024, CURRENT_TIMESTAMP)
            """,
        fileId, fileKey);
    return fileId;
  }

  private UUID insertUser() {
    UUID userId = UUID.randomUUID();
    userIds.add(userId);
    jdbcTemplate.update(
        """
            INSERT INTO users (id, email, nickname, role, enabled, created_at, profile_image_id)
            VALUES (?, ?, 'tester', 'USER', true, CURRENT_TIMESTAMP, NULL)
            """,
        userId, userId + "@test.example");
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

  private UUID insertContent(String title, String thumbnailUrl, UUID fileInfoId) {
    UUID contentId = UUID.randomUUID();
    contentIds.add(contentId);
    jdbcTemplate.update(
        """
            INSERT INTO contents
              (id, type, title, description, thumbnail_url, provider, provider_item_id,
               created_at, file_info_id)
            VALUES (?, 'movie', ?, '설명', ?, 'ADMIN', NULL, CURRENT_TIMESTAMP, ?)
            """,
        contentId, title, thumbnailUrl, fileInfoId);
    return contentId;
  }

  private void insertTag(UUID contentId, String tag) {
    jdbcTemplate.update(
        "INSERT INTO content_tags (id, content_id, tag) VALUES (?, ?, ?)",
        UUID.randomUUID(), contentId, tag);
  }

  /** 리뷰는 (콘텐츠, 작성자)가 유일해야 하므로 리뷰마다 새 작성자를 만든다. */
  private void insertReview(UUID contentId, double rating) {
    UUID authorId = insertUser();
    jdbcTemplate.update(
        """
            INSERT INTO reviews (id, content_id, author_id, text, rating, created_at)
            VALUES (?, ?, ?, '리뷰', ?, CURRENT_TIMESTAMP)
            """,
        UUID.randomUUID(), contentId, authorId, rating);
  }

  private void linkContent(UUID playlistId, UUID contentId, int minutesAfterBase) {
    jdbcTemplate.update(
        """
            INSERT INTO playlist_contents (id, playlist_id, content_id, created_at)
            VALUES (?, ?, ?, ?)
            """,
        UUID.randomUUID(), playlistId, contentId,
        Timestamp.valueOf(FIXED_AT.plusMinutes(minutesAfterBase)));
  }
}