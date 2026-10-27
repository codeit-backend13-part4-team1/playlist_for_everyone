package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class PlaylistServiceImplTest {

  @Mock
  private PlaylistRepository playlistRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private SubscriptionRepository subscriptionRepository;

  @InjectMocks
  private PlaylistServiceImpl playlistService;

  @Test
  @DisplayName("요청한 값과 소유자 ID로 플레이리스트를 저장한다.")
  void 플레이리스트_생성시_요청값과_소유자로_저장한다() {
    // given
    UUID ownerId = UUID.randomUUID();
    User owner = createUser(ownerId);
    PlaylistCreateRequest request = new PlaylistCreateRequest("제목", "설명");

    given(userRepository.findById(ownerId)).willReturn(Optional.of(owner));
    given(playlistRepository.save(any(Playlist.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    playlistService.create(ownerId, request);

    // then
    ArgumentCaptor<Playlist> captor = ArgumentCaptor.forClass(Playlist.class);
    verify(playlistRepository).save(captor.capture());
    Playlist saved = captor.getValue();
    assertThat(saved.getOwnerId()).isEqualTo(ownerId);
    assertThat(saved.getTitle()).isEqualTo("제목");
    assertThat(saved.getDescription()).isEqualTo("설명");
  }

  @Test
  @DisplayName("생성시 요청값과 소유자 정보를 응답으로 반환한다.")
  void 플레이리스트_생성시_응답에_요청값과_소유자정보가_담긴다() {
    // given
    UUID ownerId = UUID.randomUUID();
    User owner = createUser(ownerId);
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");

    given(userRepository.findById(ownerId)).willReturn(Optional.of(owner));
    given(playlistRepository.save(any(Playlist.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    PlaylistResponse response = playlistService.create(ownerId, request);

    // then
    assertThat(response.title()).isEqualTo("title");
    assertThat(response.description()).isEqualTo("description");
    assertThat(response.owner().userId()).isEqualTo(ownerId);
    assertThat(response.owner().name()).isEqualTo("tester");
    assertThat(response.owner().profileImageUrl()).isNull();
    assertThat(response.subscriberCount()).isZero();
    assertThat(response.subscribedByMe()).isFalse();
  }

  @Test
  @DisplayName("생성시 소유자가 없으면 UserNotFoundException를 던진다")
  void 플레이리스트_생성시_소유자가_없으면_예외를_던지고_save는_호출되지_않는다() {
    // given
    UUID ownerId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");
    given(userRepository.findById(ownerId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> playlistService.create(ownerId, request))
        .isInstanceOf(UserNotFoundException.class);
    verify(playlistRepository, never()).save(any());
  }

  @Test
  @DisplayName("정상적인 단건 조회시 플레이리스트와 소유자 정보를 반환한다.")
  void 단건조회시_플레이리스트와_소유자정보를_반환한다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    User owner = createUser(ownerId);
    Playlist playlist = createPlaylist(owner, playlistId);

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));

    // when
    PlaylistResponse playlistResponse = playlistService.getPlaylist(playlistId, requesterId);

    // then
    assertThat(playlistResponse.id()).isEqualTo(playlistId);
    assertThat(playlistResponse.title()).isEqualTo("title");
    assertThat(playlistResponse.description()).isEqualTo("description");
    assertThat(playlistResponse.subscriberCount()).isEqualTo(3L);
    assertThat(playlistResponse.updatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 12, 0));
    assertThat(playlistResponse.owner().userId()).isEqualTo(ownerId);
    assertThat(playlistResponse.owner().name()).isEqualTo("tester");
  }

  @Test
  @DisplayName("구독중인 플레이리스트 확인")
  void 구독중인_플레이리스트는_subscribedByMe가_true이다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    User owner = createUser(ownerId);
    Playlist playlist = createPlaylist(owner, playlistId);

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));
    given(subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId)).willReturn(true);

    // when
    PlaylistResponse playlistResponse = playlistService.getPlaylist(playlistId, requesterId);

    // then
    assertThat(playlistResponse.subscribedByMe()).isTrue();
  }

  @Test
  @DisplayName("구독하지 않은 플레이리스트 확인")
  void 구독중이_아닌_플레이리스트는_subscribedByMe가_false이다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    User owner = createUser(ownerId);
    Playlist playlist = createPlaylist(owner, playlistId);

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));
    given(subscriptionRepository
        .existsByPlaylistIdAndSubscriberId(playlistId, requesterId)).willReturn(false);

    // when
    PlaylistResponse playlistResponse = playlistService.getPlaylist(playlistId, requesterId);

    // then
    assertThat(playlistResponse.subscribedByMe()).isFalse();
  }

  @Test
  @DisplayName("없는 플레이리스트 조회")
  void 없는_플레이리스트_조회시_PlayListNotFoundException를_던진다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(PlayListNotFoundException.class)
        .isThrownBy(() -> playlistService.getPlaylist(playlistId, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId));

    verify(subscriptionRepository, never())
        .existsByPlaylistIdAndSubscriberId(any(), any());
  }

  //헬퍼 메서드
  private User createUser(UUID ownerId) {
    User owner = User.builder()
        .email("test@test.com")
        .name("tester")
        .role(Role.USER)
        .build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    return owner;
  }

  private Playlist createPlaylist(User owner, UUID playlistId) {
    Playlist playlist = new Playlist(owner.getId(), "title", "description");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    ReflectionTestUtils.setField(playlist, "owner", owner);
    ReflectionTestUtils.setField(playlist, "subscriberCount", 3L);
    ReflectionTestUtils.setField(playlist, "updatedAt", LocalDateTime.of(2026, 10, 26, 12, 0));
    return playlist;
  }
}