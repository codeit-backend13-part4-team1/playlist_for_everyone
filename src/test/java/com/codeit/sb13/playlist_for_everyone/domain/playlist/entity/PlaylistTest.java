package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Playlist 엔티티")
public class PlaylistTest {

  @Test
  @DisplayName("생성하면 구독자 수는 0이다.")
  void 생성시_구독자_수는_0이다() {
    // given
    UUID ownerId = UUID.randomUUID();

    // when
    Playlist playlist = new Playlist(ownerId, "하반기 먼치킨", "2026년 하반기 먼치킨 모음");

    // then
    assertThat(playlist.getOwnerId()).isEqualTo(ownerId);
    assertThat(playlist.getTitle()).isEqualTo("하반기 먼치킨");
    assertThat(playlist.getDescription()).isEqualTo("2026년 하반기 먼치킨 모음");
    assertThat(playlist.getSubscriberCount()).isZero();

  }

}
