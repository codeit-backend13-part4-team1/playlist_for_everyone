package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Subscription;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("SubscriptionRepository")
class SubscriptionRepositoryTest {

  @Autowired
  private SubscriptionRepository subscriptionRepository;

  @Test
  @DisplayName("저장하면 ID와 생성 시각이 기록된다")
  void 저장하면_ID와_생성_시각이_기록된다() {
    Subscription saved = subscriptionRepository.saveAndFlush(
        new Subscription(UUID.randomUUID(), UUID.randomUUID()));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("같은 사용자가 같은 플레이리스트를 중복 구독할 수 없다")
  void 중복_구독할_수_없다() {
    UUID playlistId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    subscriptionRepository.saveAndFlush(new Subscription(playlistId, subscriberId));

    assertThatThrownBy(() ->
        subscriptionRepository.saveAndFlush(new Subscription(playlistId, subscriberId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("다른 사용자는 같은 플레이리스트를 구독할 수 있다")
  void 다른_사용자는_구독할_수_있다() {
    UUID playlistId = UUID.randomUUID();
    subscriptionRepository.saveAndFlush(new Subscription(playlistId, UUID.randomUUID()));

    assertThatCode(() ->
        subscriptionRepository.saveAndFlush(new Subscription(playlistId, UUID.randomUUID())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("사용자가 이 플레이리스트를 구독하는 중이라면 true")
  void 사용자가_이_플레이리스트를_구독하는_중이라면_true를_반환한다(){
    // given
    UUID playlistId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    subscriptionRepository.saveAndFlush(
        new Subscription(playlistId, subscriberId));

    // when
    boolean subscriptionStatus = subscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId,
        subscriberId);

    // then
    assertThat(subscriptionStatus).isTrue();

  }

  @Test
  @DisplayName("사용자가 이 플레이리스트를 구독하지 않는 중이라면 false")
  void 사용자가_이_플레이리스트를_구독하지_않는_중이라면_false를_반환한다(){
    // given
    UUID playlistId = UUID.randomUUID();
    UUID subscriberId1 = UUID.randomUUID();
    UUID subscriberId2 = UUID.randomUUID();
     subscriptionRepository.saveAndFlush(
        new Subscription(playlistId, subscriberId1));

    // when
    boolean subscriptionStatus = subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, subscriberId2);

    // then
    assertThat(subscriptionStatus).isFalse();

  }

  @Test
  @DisplayName("주어진 플레이리스트 중 해당 사용자가 구독한 플레이리스트 ID만 조회한다")
  void 주어진_플레이리스트_중_사용자가_구독한_ID만_조회한다() {
    // given
    UUID subscriberId = UUID.randomUUID();
    UUID otherSubscriberId = UUID.randomUUID();
    UUID playlistA = UUID.randomUUID();
    UUID playlistB = UUID.randomUUID();
    UUID playlistC = UUID.randomUUID();
    UUID playlistD = UUID.randomUUID();
    subscriptionRepository.save(new Subscription(playlistA, subscriberId));
    subscriptionRepository.save(new Subscription(playlistB, subscriberId));
    subscriptionRepository.save(new Subscription(playlistC, subscriberId));
    subscriptionRepository.saveAndFlush(new Subscription(playlistD, otherSubscriberId));

    // when
    List<UUID> result = subscriptionRepository.findSubscribedPlaylistIds(
        subscriberId, List.of(playlistA, playlistB, playlistD));

    // then
    assertThat(result).containsExactlyInAnyOrder(playlistA, playlistB);
  }

  @Test
  @DisplayName("플레이리스트 ID로 구독을 모두 삭제하고 다른 플레이리스트의 구독은 남긴다")
  void 플레이리스트_ID로_구독을_모두_삭제한다() {
    // given
    UUID targetId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    subscriptionRepository.saveAndFlush(new Subscription(targetId, UUID.randomUUID()));
    subscriptionRepository.saveAndFlush(new Subscription(targetId, UUID.randomUUID()));
    Subscription other = subscriptionRepository.saveAndFlush(
        new Subscription(otherId, UUID.randomUUID()));

    // when
    int deleted = subscriptionRepository.deleteAllByPlaylistId(targetId);

    // then
    assertThat(deleted).isEqualTo(2);
    assertThat(subscriptionRepository.findAll())
        .extracting(Subscription::getId)
        .containsExactly(other.getId());
  }

  @Test
  @DisplayName("삭제할 구독이 없으면 0을 반환한다")
  void 삭제할_구독이_없으면_0을_반환한다() {
    // when
    int deleted = subscriptionRepository.deleteAllByPlaylistId(UUID.randomUUID());

    // then
    assertThat(deleted).isZero();
  }

  @Test
  @DisplayName("플레이리스트와 구독자로 구독을 삭제하고 다른 구독은 남긴다")
  void 플레이리스트와_구독자로_구독을_삭제한다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID otherPlaylistId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    UUID otherSubscriberId = UUID.randomUUID();
    subscriptionRepository.saveAndFlush(new Subscription(playlistId, subscriberId));
    Subscription otherSubscriber = subscriptionRepository.saveAndFlush(
        new Subscription(playlistId, otherSubscriberId));
    Subscription otherPlaylist = subscriptionRepository.saveAndFlush(
        new Subscription(otherPlaylistId, subscriberId));

    // when
    int deleted = subscriptionRepository.deleteByPlaylistIdAndSubscriberId(
        playlistId, subscriberId);

    // then
    assertThat(deleted).isEqualTo(1);
    assertThat(subscriptionRepository.findAll())
        .extracting(Subscription::getId)
        .containsExactlyInAnyOrder(otherSubscriber.getId(), otherPlaylist.getId());
  }

  @Test
  @DisplayName("삭제할 구독이 없으면 0을 반환한다")
  void 구독자의_구독이_없으면_삭제_건수는_0이다() {
    // given
    UUID playlistId = UUID.randomUUID();
    subscriptionRepository.saveAndFlush(new Subscription(playlistId, UUID.randomUUID()));

    // when
    int deleted = subscriptionRepository.deleteByPlaylistIdAndSubscriberId(
        playlistId, UUID.randomUUID());

    // then
    assertThat(deleted).isZero();
    assertThat(subscriptionRepository.findAll()).hasSize(1);
  }

}
