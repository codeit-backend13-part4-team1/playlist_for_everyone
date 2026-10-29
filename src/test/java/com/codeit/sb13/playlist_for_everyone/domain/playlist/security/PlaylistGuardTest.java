package com.codeit.sb13.playlist_for_everyone.domain.playlist.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaylistGuard")
class PlaylistGuardTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @InjectMocks
  private PlaylistGuard playlistGuard;

  @Test
  @DisplayName("소유자이면 true를 반환한다")
  void 소유자이면_true를_반환한다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    given(playlistRepository.findById(playlistId))
        .willReturn(Optional.of(createPlaylist(ownerId, playlistId)));

    // when & then
    assertThat(playlistGuard.canModify(playlistId, ownerId)).isTrue();
  }

  @Test
  @DisplayName("소유자가 아니면 false를 반환한다")
  void 소유자가_아니면_false를_반환한다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    given(playlistRepository.findById(playlistId))
        .willReturn(Optional.of(createPlaylist(ownerId, playlistId)));

    // when & then
    assertThat(playlistGuard.canModify(playlistId, requesterId)).isFalse();
  }

  @Test
  @DisplayName("플레이리스트가 없으면 true를 반환해 서비스가 404를 처리하게 한다")
  void 플레이리스트가_없으면_true를_반환한다() {
    // given
    UUID playlistId = UUID.randomUUID();
    given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

    // when & then
    assertThat(playlistGuard.canModify(playlistId, UUID.randomUUID())).isTrue();
  }

  private Playlist createPlaylist(UUID ownerId, UUID playlistId) {
    User owner = User.builder()
        .email("test@test.com")
        .name("tester")
        .role(Role.USER)
        .build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    Playlist playlist = new Playlist(owner, "title", "description");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    return playlist;
  }
}