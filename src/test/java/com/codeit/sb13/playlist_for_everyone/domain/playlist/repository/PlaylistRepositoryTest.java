package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@DisplayName("PlaylistRepository")
public class PlaylistRepositoryTest {

  @Test
  @DisplayName("저장하면 ID와 생성,수정 시각이 기록되고 구독자 수는 0이다.")
  void 저장하면_ID와_시각이_기록되고_구독자_수는_0이다() {
    // when
    Playlist saved = playlistRepository.saveAndFlush(
        new Playlist(UUID.randomUUID(), "제목", "설명")
    );

    // then
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(saved.getSubscriberCount()).isZero();


  }

}
