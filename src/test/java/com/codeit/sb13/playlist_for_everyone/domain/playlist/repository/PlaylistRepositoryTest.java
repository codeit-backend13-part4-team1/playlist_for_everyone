package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("PlaylistRepository")
public class PlaylistRepositoryTest {

  @Autowired
  private PlaylistRepository playlistRepository;

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
