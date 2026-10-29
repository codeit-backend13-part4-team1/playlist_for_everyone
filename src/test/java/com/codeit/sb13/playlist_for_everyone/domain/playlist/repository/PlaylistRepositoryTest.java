package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.util.Optional;


@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("PlaylistRepository")
public class PlaylistRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PlaylistRepository playlistRepository;

  @PersistenceContext
  private EntityManager em;

  @Test
  @DisplayName("id로 조회하면 소유자가 함께 로딩된다")
  void id로_조회하면_소유자가_함께_로딩된다() {
    // given
    User owner = userRepository.save(User.builder()
        .email("owner@test.com")
        .name("tester")
        .role(Role.USER)
        .build());
    Playlist saved = playlistRepository.save(
        new Playlist(owner, "제목", "설명"));
    em.flush();
    em.clear();   // 영속성 컨텍스트를 비워서 실제 조회 쿼리가 나가게 한다

    // when
    Optional<Playlist> found = playlistRepository.findWithOwnerById(saved.getId());

    // then
    assertThat(found).isPresent();
    assertThat(Hibernate.isInitialized(found.get().getOwner())).isTrue();
    assertThat(found.get().getOwner().getName()).isEqualTo("tester");
  }

  @Test
  @DisplayName("없는 id로 조회하면 빈 결과를 반환한다")
  void 없는_id로_조회하면_빈_결과를_반환한다() {
    // when
    Optional<Playlist> found = playlistRepository.findWithOwnerById(UUID.randomUUID());

    // then
    assertThat(found).isEmpty();
  }


  @Test
  @DisplayName("저장하면 ID와 생성,수정 시각이 기록되고 구독자 수는 0이다.")
  void 저장하면_ID와_시각이_기록되고_구독자_수는_0이다() {
    //given
    User owner = userRepository.save(User.builder()
        .email("saver@test.com")
        .name("tester")
        .role(Role.USER)
        .build());

    // when
    Playlist saved = playlistRepository.saveAndFlush(
        new Playlist(owner, "제목", "설명")
    );

    // then
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(saved.getSubscriberCount()).isZero();
  }

  @Test
  @DisplayName("수정일 갱신 쿼리는 해당 플레이리스트의 수정일을 주어진 시각으로 바꾼다")
  void 수정일_갱신_쿼리는_수정일을_주어진_시각으로_바꾼다() {
    // given
    User owner = userRepository.save(User.builder()
        .email("touch@test.com")
        .name("tester")
        .role(Role.USER)
        .build());
    Playlist saved = playlistRepository.saveAndFlush(new Playlist(owner, "제목", "설명"));
    em.clear();
    LocalDateTime newTime = LocalDateTime.of(2030, 1, 1, 12, 0);

    // when
    int updated = playlistRepository.touchUpdatedAt(saved.getId(), newTime);

    // then
    assertThat(updated).isEqualTo(1);
    Playlist found = playlistRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getUpdatedAt()).isEqualTo(newTime);
  }

  @Test
  @DisplayName("없는 플레이리스트면 0을 반환한다")
  void 없는_플레이리스트면_수정일_갱신은_0을_반환한다() {
    // when
    int updated = playlistRepository.touchUpdatedAt(UUID.randomUUID(), LocalDateTime.now());

    // then
    assertThat(updated).isZero();
  }

}
