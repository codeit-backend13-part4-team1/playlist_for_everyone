package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Playlist 엔티티")
public class PlaylistTest {

  @Test
  @DisplayName("생성하면 구독자 수는 0이다.")
  void 생성시_구독자_수는_0이다() {
    // given
    User owner = User.builder()
        .email("owner@owner.com")
        .name("tester")
        .role(Role.USER)
        .build();

    // when
    Playlist playlist = new Playlist(owner, "하반기 먼치킨", "2026년 하반기 먼치킨 모음");

    // then
    assertThat(playlist.getOwner()).isEqualTo(owner);
    assertThat(playlist.getTitle()).isEqualTo("하반기 먼치킨");
    assertThat(playlist.getDescription()).isEqualTo("2026년 하반기 먼치킨 모음");
    assertThat(playlist.getSubscriberCount()).isZero();

  }

}
