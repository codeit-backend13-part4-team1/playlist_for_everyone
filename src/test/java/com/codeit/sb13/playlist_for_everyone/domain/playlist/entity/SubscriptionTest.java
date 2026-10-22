package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Subscription 엔티티")
public class SubscriptionTest {

  @Test
  @DisplayName("플레이리스트 ID와 구독자 ID로 생성한다")
  void 생성시_플레이리스트_ID와_구독자_ID를_가진다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    // when
    Subscription subscription = new Subscription(playlistId, subscriberId);

    // then
    assertThat(subscription.getPlaylistId()).isEqualTo(playlistId);
    assertThat(subscription.getSubscriberId()).isEqualTo(subscriberId);

  }

}
