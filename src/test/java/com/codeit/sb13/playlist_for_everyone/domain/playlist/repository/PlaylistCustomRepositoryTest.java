package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Subscription;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class PlaylistCustomRepositoryTest {

  @Autowired
  private PlaylistRepository playlistRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager em;

  @Autowired
  private SubscriptionRepository subscriptionRepository;

  @Test
  @DisplayName("필터가 없으면 updatedAt 내림차순으로 조회한다")
  void 필터가_없으면_updatedAt_내림차순으로_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    savePlaylist(owner, "oldest", LocalDateTime.of(2026, 10, 1, 0, 0));
    savePlaylist(owner, "middle", LocalDateTime.of(2026, 10, 2, 0, 0));
    savePlaylist(owner, "latest", LocalDateTime.of(2026, 10, 3, 0, 0));

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        null, null,
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactly("latest", "middle", "oldest");
  }

  @Test
  @DisplayName("subscribeCount 내림차순으로 조회한다")
  void subscribeCount_내림차순으로_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    savePlaylistWithSubscribers(owner, "low", 1);
    savePlaylistWithSubscribers(owner, "high", 30);
    savePlaylistWithSubscribers(owner, "middle", 5);

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        null, null,
        10, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactly("high", "middle", "low");
  }

  @Test
  @DisplayName("subscribeCount가 같아도 id 기준으로 이어서 중복과 누락 없이 조회한다")
  void subscribeCount가_같아도_id_기준으로_이어서_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    Set<UUID> savedIds = Set.of(
        savePlaylistWithSubscribers(owner, "p1", 7).getId(),
        savePlaylistWithSubscribers(owner, "p2", 7).getId(),
        savePlaylistWithSubscribers(owner, "p3", 7).getId());

    // when
    Playlist first = searchPage(PlaylistSortBy.SUBSCRIBE_COUNT, null, null).get(0);
    Playlist second = searchPage(PlaylistSortBy.SUBSCRIBE_COUNT, "7", first.getId()).get(0);
    Playlist third = searchPage(PlaylistSortBy.SUBSCRIBE_COUNT, "7", second.getId()).get(0);
    List<Playlist> afterLast = searchPage(PlaylistSortBy.SUBSCRIBE_COUNT, "7", third.getId());

    // then
    assertThat(List.of(first.getId(), second.getId(), third.getId()))
        .containsExactlyInAnyOrderElementsOf(savedIds);
    assertThat(afterLast).isEmpty();
  }

  @Test
  @DisplayName("keywordLike로 제목이나 설명에 키워드가 포함된 플레이리스트를 대소문자 구분 없이 조회한다")
  void keywordLike로_제목이나_설명에_포함된_플레이리스트를_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    savePlaylistWithDescription(owner, "Rock Classics", "옛날 노래");
    savePlaylistWithDescription(owner, "Chill", "best ROCK songs");
    savePlaylistWithDescription(owner, "Jazz", "smooth");

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        "rock", null, null,
        null, null,
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactlyInAnyOrder("Rock Classics", "Chill");
  }

  @Test
  @DisplayName("ownerIdEqual로 해당 소유자의 플레이리스트만 조회한다")
  void ownerIdEqual로_해당_소유자의_플레이리스트만_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    User other = saveUser("other@test.com");
    savePlaylistWithDescription(owner, "mine1", "설명");
    savePlaylistWithDescription(owner, "mine2", "설명");
    savePlaylistWithDescription(other, "others", "설명");

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, owner.getId(), null,
        null, null,
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactlyInAnyOrder("mine1", "mine2");
  }


  @Test
  @DisplayName("subscriberIdEqual로 해당 사용자가 구독한 플레이리스트만 조회한다")
  void subscriberIdEqual로_해당_사용자가_구독한_플레이리스트만_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    User subscriber = saveUser("subscriber@test.com");
    User other = saveUser("other@test.com");
    Playlist p1 = savePlaylistWithDescription(owner, "p1", "설명");
    Playlist p2 = savePlaylistWithDescription(owner, "p2", "설명");
    Playlist p3 = savePlaylistWithDescription(owner, "p3", "설명");
    subscriptionRepository.save(new Subscription(p1.getId(), subscriber.getId()));
    subscriptionRepository.save(new Subscription(p3.getId(), subscriber.getId()));
    subscriptionRepository.save(new Subscription(p2.getId(), other.getId()));

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, subscriber.getId(),
        null, null,
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactlyInAnyOrder("p1", "p3");
  }

  @Test
  @DisplayName("updatedAt 내림차순에서 커서 이후의 플레이리스트만 조회한다")
  void updatedAt_내림차순에서_커서_이후의_플레이리스트만_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    savePlaylist(owner, "oldest", LocalDateTime.of(2026, 10, 1, 0, 0));
    Playlist middle = savePlaylist(owner, "middle", LocalDateTime.of(2026, 10, 2, 0, 0));
    savePlaylist(owner, "latest", LocalDateTime.of(2026, 10, 3, 0, 0));

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        "2026-10-02T00:00:00", middle.getId(),
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactly("oldest");
  }

  @Test
  @DisplayName("updatedAt이 같아도 id 기준으로 이어서 중복과 누락 없이 조회한다")
  void updatedAt이_같아도_id_기준으로_이어서_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    LocalDateTime sameTime = LocalDateTime.of(2026, 10, 1, 0, 0);
    String cursor = "2026-10-01T00:00:00";
    Set<UUID> savedIds = Set.of(
        savePlaylist(owner, "p1", sameTime).getId(),
        savePlaylist(owner, "p2", sameTime).getId(),
        savePlaylist(owner, "p3", sameTime).getId());

    // when
    Playlist first = searchPage(null, null).get(0);
    Playlist second = searchPage(cursor, first.getId()).get(0);
    Playlist third = searchPage(cursor, second.getId()).get(0);
    List<Playlist> afterLast = searchPage(cursor, third.getId());

    // then
    assertThat(List.of(first.getId(), second.getId(), third.getId()))
        .containsExactlyInAnyOrderElementsOf(savedIds);
    assertThat(afterLast).isEmpty();
  }

  @Test
  @DisplayName("subscribeCount 내림차순에서 커서 이후의 플레이리스트만 조회한다")
  void subscribeCount_내림차순에서_커서_이후의_플레이리스트만_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    savePlaylistWithSubscribers(owner, "low", 1);
    Playlist middle = savePlaylistWithSubscribers(owner, "middle", 5);
    savePlaylistWithSubscribers(owner, "high", 30);

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        "5", middle.getId(),
        10, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactly("low");
  }

  @Test
  @DisplayName("limit보다 하나 더 많은 개수까지 조회한다")
  void limit보다_하나_더_많은_개수까지_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    for (int i = 1; i <= 5; i++) {
      savePlaylistWithDescription(owner, "playlist" + i, "설명");
    }

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        null, null,
        2, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result).hasSize(3);
  }

  @Test
  @DisplayName("count는 커서와 limit을 무시하고 필터 조건에 맞는 전체 개수를 센다")
  void count는_커서와_limit을_무시하고_필터_조건에_맞는_전체_개수를_센다() {
    // given
    User owner = saveUser("owner@test.com");
    User other = saveUser("other@test.com");
    savePlaylist(owner, "oldest", LocalDateTime.of(2026, 10, 1, 0, 0));
    Playlist middle = savePlaylist(owner, "middle", LocalDateTime.of(2026, 10, 2, 0, 0));
    savePlaylist(owner, "latest", LocalDateTime.of(2026, 10, 3, 0, 0));
    savePlaylistWithDescription(other, "others", "설명");

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, owner.getId(), null,
        "2026-10-02T00:00:00", middle.getId(),
        1, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    long count = playlistRepository.count(condition);

    // then
    assertThat(count).isEqualTo(3L);
  }

  @Test
  @DisplayName("조회한 플레이리스트의 소유자를 함께 가져온다")
  void 조회한_플레이리스트의_소유자를_함께_가져온다() {
    // given
    User owner1 = saveUser("owner1@test.com");
    User owner2 = saveUser("owner2@test.com");
    savePlaylistWithDescription(owner1, "p1", "설명");
    savePlaylistWithDescription(owner2, "p2", "설명");
    em.flush();
    em.clear();

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        null, null,
        10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result).hasSize(2);
    assertThat(result).allSatisfy(playlist ->
        assertThat(Hibernate.isInitialized(playlist.getOwner())).isTrue());
  }

  @Test
  @DisplayName("updatedAt 오름차순에서 커서 이후의 플레이리스트만 조회한다")
  void updatedAt_오름차순에서_커서_이후의_플레이리스트만_조회한다() {
    // given
    User owner = saveUser("owner@test.com");
    savePlaylist(owner, "oldest", LocalDateTime.of(2026, 10, 1, 0, 0));
    Playlist middle = savePlaylist(owner, "middle", LocalDateTime.of(2026, 10, 2, 0, 0));
    savePlaylist(owner, "latest", LocalDateTime.of(2026, 10, 3, 0, 0));

    PlaylistSearchCondition condition = new PlaylistSearchCondition(
        null, null, null,
        "2026-10-02T00:00:00", middle.getId(),
        10, PlaylistSortBy.UPDATED_AT, SortDirection.ASCENDING);

    // when
    List<Playlist> result = playlistRepository.search(condition);

    // then
    assertThat(result)
        .extracting(Playlist::getTitle)
        .containsExactly("latest");
  }


  //----------[헬퍼 메사드]
  private User saveUser(String email) {
    return userRepository.save(User.builder()
        .email(email)
        .name("tester")
        .role(Role.USER)
        .build());
  }

  private Playlist savePlaylist(User owner, String title, LocalDateTime updatedAt) {
    Playlist playlist = playlistRepository.saveAndFlush(new Playlist(owner, title, "설명"));
    em.createQuery("update Playlist p set p.updatedAt = :updatedAt where p.id = :id")
        .setParameter("updatedAt", updatedAt)
        .setParameter("id", playlist.getId())
        .executeUpdate();
    em.clear();
    return playlist;
  }

  private Playlist savePlaylistWithSubscribers(User owner, String title, long subscriberCount) {
    Playlist playlist = new Playlist(owner, title, "설명");
    ReflectionTestUtils.setField(playlist, "subscriberCount", subscriberCount);
    return playlistRepository.saveAndFlush(playlist);
  }

  private Playlist savePlaylistWithDescription(User owner, String title, String description) {
    return playlistRepository.saveAndFlush(new Playlist(owner, title, description));
  }

  private List<Playlist> searchPage(String cursor, UUID idAfter) {
    return searchPage(PlaylistSortBy.UPDATED_AT, cursor, idAfter);
  }

  private List<Playlist> searchPage(PlaylistSortBy sortBy, String cursor, UUID idAfter) {
    return playlistRepository.search(new PlaylistSearchCondition(
        null, null, null,
        cursor, idAfter,
        1, sortBy, SortDirection.DESCENDING));
  }
}