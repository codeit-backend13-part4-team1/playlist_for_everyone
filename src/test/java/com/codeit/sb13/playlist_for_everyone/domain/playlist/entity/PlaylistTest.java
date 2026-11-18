package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  @DisplayName("title과 description을 모두 수정하면 둘 다 바뀐다")
  void 제목과_설명을_모두_수정하면_둘_다_바뀐다() {
    // given
    Playlist playlist = new Playlist(createOwner(), "기존 제목", "기존 설명");

    // when
    playlist.update("새 제목", "새 설명");

    // then
    assertThat(playlist.getTitle()).isEqualTo("새 제목");
    assertThat(playlist.getDescription()).isEqualTo("새 설명");
  }

  @Test
  @DisplayName("title만 수정하면 description은 그대로이다")
  void 제목만_수정하면_설명은_그대로이다() {
    // given
    Playlist playlist = new Playlist(createOwner(), "기존 제목", "기존 설명");

    // when
    playlist.update("새 제목", null);

    // then
    assertThat(playlist.getTitle()).isEqualTo("새 제목");
    assertThat(playlist.getDescription()).isEqualTo("기존 설명");
  }

  @Test
  @DisplayName("description만 수정하면 title은 그대로이다")
  void 설명만_수정하면_제목은_그대로이다() {
    // given
    Playlist playlist = new Playlist(createOwner(), "기존 제목", "기존 설명");

    // when
    playlist.update(null, "새 설명");

    // then
    assertThat(playlist.getTitle()).isEqualTo("기존 제목");
    assertThat(playlist.getDescription()).isEqualTo("새 설명");
  }

  @Test
  @DisplayName("소유자의 ID로 확인하면 true이다")
  void 소유자_ID면_true를_반환한다() {
    // given
    User owner = createOwner();
    Playlist playlist = new Playlist(owner, "제목", "설명");

    // when
    boolean result = playlist.isOwnedBy(owner.getId());

    // then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("소유자가 아닌 ID로 확인하면 false이다")
  void 소유자가_아닌_ID면_false를_반환한다() {
    // given
    Playlist playlist = new Playlist(createOwner(), "제목", "설명");

    // when
    boolean result = playlist.isOwnedBy(UUID.randomUUID());

    // then
    assertThat(result).isFalse();
  }


  //--------------[헬퍼 메서드]
  private User createOwner() {
    User owner = User.builder()
        .email("owner@owner.com")
        .name("tester")
        .role(Role.USER)
        .build();
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    return owner;
  }

}
