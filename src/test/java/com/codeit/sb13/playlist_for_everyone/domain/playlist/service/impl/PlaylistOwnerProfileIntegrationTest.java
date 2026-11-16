package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

  // getFileUrl이 호출되던 순간의 트랜잭션 활성 여부를 호출 순서대로 기록한다.
  private final List<Boolean> transactionStatesAtUrlGeneration = new ArrayList<>();

  @BeforeEach
  void setUp() {
    given(fileStorage.getFileUrl(anyString()))
        .willAnswer(invocation -> {
          transactionStatesAtUrlGeneration.add(
              TransactionSynchronizationManager.isActualTransactionActive());
          return "https://cdn.test/" + invocation.getArgument(0);
        });
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
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
    // + 플레이리스트 콘텐츠 연결 1회”를 추가.
    assertThat(statementCount).isLessThanOrEqualTo(4L);
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

  @Test
  @DisplayName("생성 응답의 프로필 이미지 URL은 트랜잭션 밖에서 생성된다")
  void 생성_응답의_프로필_이미지_URL은_트랜잭션_밖에서_생성된다() {
    // given
    UUID ownerId = insertUserWithProfileImage("profile/create.png");

    // when
    PlaylistResponse response = playlistService.create(
        ownerId, new PlaylistCreateRequest("제목", "설명"));
    playlistIds.add(response.id());

    // then
    assertThat(response.owner().profileImageUrl()).isEqualTo("https://cdn.test/profile/create.png");
    assertThat(transactionStatesAtUrlGeneration).containsExactly(false);
  }

  @Test
  @DisplayName("단건 조회 응답의 프로필 이미지 URL은 트랜잭션 밖에서 생성된다")
  void 단건_조회_응답의_프로필_이미지_URL은_트랜잭션_밖에서_생성된다() {
    // given
    UUID ownerId = insertUserWithProfileImage("profile/get.png");
    UUID playlistId = insertPlaylist(ownerId);

    // when
    playlistService.getPlaylist(playlistId, UUID.randomUUID());

    // then
    assertThat(transactionStatesAtUrlGeneration).containsExactly(false);
  }

  @Test
  @DisplayName("목록 조회 응답의 프로필 이미지 URL은 트랜잭션 밖에서 생성된다")
  void 목록_조회_응답의_프로필_이미지_URL은_트랜잭션_밖에서_생성된다() {
    // given
    UUID ownerId = insertUserWithProfileImage("profile/list.png");
    insertPlaylist(ownerId);
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, ownerId, null, null, null,
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    playlistService.getPlaylists(request, UUID.randomUUID());

    // then
    assertThat(transactionStatesAtUrlGeneration).containsExactly(false);
  }

  @Test
  @DisplayName("프로필 이미지 URL 생성이 실패해도 저장된 플레이리스트는 롤백되지 않는다")
  void 프로필_이미지_URL_생성이_실패해도_저장된_플레이리스트는_롤백되지_않는다() {
    // given
    UUID ownerId = insertUserWithProfileImage("profile/fail.png");
    willThrow(new IllegalStateException("URL 생성 실패"))
        .given(fileStorage).getFileUrl(anyString());

    // when
    assertThatThrownBy(() -> playlistService.create(
        ownerId, new PlaylistCreateRequest("제목", "설명")))
        .isInstanceOf(IllegalStateException.class);

    // then: 저장은 이미 커밋되어 있어야 한다.
    List<UUID> saved = jdbcTemplate.queryForList(
        "SELECT id FROM playlists WHERE owner_id = ?", UUID.class, ownerId);
    playlistIds.addAll(saved);
    assertThat(saved).hasSize(1);
  }

  @Test
  @DisplayName("소유자가 없어 저장에 실패하면 프로필 이미지 URL 생성을 시도하지 않는다")
  void 소유자가_없어_저장에_실패하면_URL_생성을_시도하지_않는다() {
    // when & then
    assertThatThrownBy(() -> playlistService.create(
        UUID.randomUUID(), new PlaylistCreateRequest("제목", "설명")))
        .isInstanceOf(UserNotFoundException.class);
    verify(fileStorage, never()).getFileUrl(anyString());
  }

  @Test
  @DisplayName("수정 응답의 프로필 이미지 URL은 트랜잭션 밖에서 생성된다")
  void 수정_응답의_프로필_이미지_URL은_트랜잭션_밖에서_생성된다() {
    // given
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("test-user", null, List.of()));
    UUID ownerId = insertUserWithProfileImage("profile/update.png");
    UUID playlistId = insertPlaylist(ownerId);

    // when
    PlaylistResponse response = playlistService.updatePlaylist(
        playlistId, new PlaylistUpdateRequest("새 제목", null), ownerId);

    // then
    assertThat(response.title()).isEqualTo("새 제목");
    assertThat(response.owner().profileImageUrl()).isEqualTo("https://cdn.test/profile/update.png");
    assertThat(transactionStatesAtUrlGeneration).containsExactly(false);
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