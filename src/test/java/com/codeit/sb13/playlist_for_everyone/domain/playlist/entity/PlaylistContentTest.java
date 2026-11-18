package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlaylistContent 엔티티")
public class PlaylistContentTest {

  @Test
  @DisplayName("플레이이스트 ID와 콘탠츠 ID로 생성한다.")
  void 생성시_플레이리스트_ID와_콘텐츠_ID를_갖는다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    // when
    PlaylistContent playlistContent = new PlaylistContent(playlistId, contentId);


    // then
    assertThat(playlistContent.getPlaylistId()).isEqualTo(playlistId);
    assertThat(playlistContent.getContentId()).isEqualTo(contentId);


  }

}
