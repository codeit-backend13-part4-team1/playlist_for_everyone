package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@DisplayName("PlaylistContentRepository")
class PlaylistContentRepositoryTest {

  @Autowired
  private PlaylistContentRepository playlistContentRepository;

  @Test
  @DisplayName("저장하면 ID와 생성 시각이 기록된다")
  void 저장하면_ID와_생성_시각이_기록된다() {
    // when
    PlaylistContent saved = playlistContentRepository.saveAndFlush(
        new PlaylistContent(UUID.randomUUID(), UUID.randomUUID()));

    // then
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("같은 플레이리스트에 같은 콘텐츠를 중복 저장할 수 없다")
  void 같은_콘텐츠를_중복_저장할_수_없다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, contentId));

    // when & then
    assertThatThrownBy(() ->
        playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, contentId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("같은 플레이리스트에 다른 콘텐츠는 저장할 수 있다")
  void 다른_콘텐츠는_저장할_수_있다() {
    // given
    UUID playlistId = UUID.randomUUID();
    playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, UUID.randomUUID()));

    // when & then
    assertThatCode(() ->
        playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, UUID.randomUUID())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("플레이리스트 ID로 콘텐츠 연결을 모두 삭제하고 다른 플레이리스트의 연결은 남긴다")
  void 플레이리스트_ID로_콘텐츠_연결을_모두_삭제한다() {
    // given
    UUID targetId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    playlistContentRepository.saveAndFlush(new PlaylistContent(targetId, UUID.randomUUID()));
    playlistContentRepository.saveAndFlush(new PlaylistContent(targetId, UUID.randomUUID()));
    PlaylistContent other = playlistContentRepository.saveAndFlush(
        new PlaylistContent(otherId, UUID.randomUUID()));

    // when
    int deleted = playlistContentRepository.deleteAllByPlaylistId(targetId);

    // then
    assertThat(deleted).isEqualTo(2);
    assertThat(playlistContentRepository.findAll())
        .extracting(PlaylistContent::getId)
        .containsExactly(other.getId());
  }

  @Test
  @DisplayName("삭제할 콘텐츠 연결이 없으면 0을 반환한다")
  void 삭제할_콘텐츠_연결이_없으면_0을_반환한다() {
    // when
    int deleted = playlistContentRepository.deleteAllByPlaylistId(UUID.randomUUID());

    // then
    assertThat(deleted).isZero();
  }

  @Test
  @DisplayName("플레이 리스트에 담긴 콘텐츠면 true를 반환한다.")
  void 담긴_콘텐츠면_true를_반환한다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, contentId));

    // when
    boolean exists = playlistContentRepository.existsByPlaylistIdAndContentId(
        playlistId, contentId);

    // then
    assertThat(exists).isTrue();

  }

  @Test
  @DisplayName("다른 콘텐츠거나, 다른 플레이리스면 false를 반환한다.")
  void 담기지_않은_콘텐츠면_false를_반환한다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, contentId));

    // when & then
   assertThat(playlistContentRepository.existsByPlaylistIdAndContentId(
       playlistId, UUID.randomUUID())).isFalse();
    assertThat(playlistContentRepository.existsByPlaylistIdAndContentId(
        UUID.randomUUID(), contentId)).isFalse();
  }

  @Test
  @DisplayName("플레이리스트와 콘텐츠로 연결 하나만 삭제하고 1을 반환한다")
  void 플레이리스트와_콘텐츠로_연결_하나만_삭제한다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID otherPlaylistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID otherContentId = UUID.randomUUID();
    playlistContentRepository.saveAndFlush(new PlaylistContent(playlistId, contentId));
    PlaylistContent sameListOtherContent = playlistContentRepository.saveAndFlush(
        new PlaylistContent(playlistId, otherContentId));
    PlaylistContent otherListSameContent = playlistContentRepository.saveAndFlush(
        new PlaylistContent(otherPlaylistId, contentId));

    // when
    int deleted = playlistContentRepository.deleteByPlaylistIdAndContentId(
        playlistId, contentId);

    // then
    assertThat(deleted).isEqualTo(1);
    assertThat(playlistContentRepository.findAll())
        .extracting(PlaylistContent::getId)
        .containsExactlyInAnyOrder(
            sameListOtherContent.getId(), otherListSameContent.getId());
  }

  @Test
  @DisplayName("삭제할 연결이 없으면 0을 반환하고 기존 연결은 그대로 둔다")
  void 삭제할_연결이_없으면_0을_반환하고_그대로_둔다() {
    // given
    UUID playlistId = UUID.randomUUID();
    PlaylistContent existing = playlistContentRepository.saveAndFlush(
        new PlaylistContent(playlistId, UUID.randomUUID()));

    // when
    int deleted = playlistContentRepository.deleteByPlaylistIdAndContentId(
        playlistId, UUID.randomUUID());

    // then
    assertThat(deleted).isZero();
    assertThat(playlistContentRepository.findAll())
        .extracting(PlaylistContent::getId)
        .containsExactly(existing.getId());
  }
}
