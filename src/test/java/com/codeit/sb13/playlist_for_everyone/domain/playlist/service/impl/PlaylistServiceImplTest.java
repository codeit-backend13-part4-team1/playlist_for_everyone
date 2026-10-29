package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistContent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentDuplicatedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListForbiddenException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class PlaylistServiceImplTest {

  @Mock
  private PlaylistRepository playlistRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private SubscriptionRepository subscriptionRepository;

  @Mock
  private PlaylistContentRepository playlistContentRepository;

  @Mock
  private ContentRepository contentRepository;

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
    assertThat(saved.getOwner()).isEqualTo(owner);
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

  @Test
  @DisplayName("조회 결과가 limit 이하이면 다음 페이지가 없다")
  void 조회_결과가_limit_이하이면_다음_페이지가_없다() {
    // given
    UUID requesterId = UUID.randomUUID();
    User owner = createUser(UUID.randomUUID());
    Playlist playlist1 = createPlaylist(owner, UUID.randomUUID());
    Playlist playlist2 = createPlaylist(owner, UUID.randomUUID());
    PlaylistSearchRequest request =
        createSearchRequest(2, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of(playlist1, playlist2));
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(2L);
    given(subscriptionRepository.findSubscribedPlaylistIds(eq(requesterId), anyCollection()))
        .willReturn(List.of());

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextCursor()).isNull();
    assertThat(response.nextIdAfter()).isNull();
    assertThat(response.data()).hasSize(2);
    assertThat(response.totalCount()).isEqualTo(2L);
  }


  @Test
  @DisplayName("조회 결과가 limit보다 많으면 limit개만 담고 마지막 항목 기준으로 다음 커서를 만든다")
  void 조회_결과가_limit보다_많으면_limit개만_담고_다음_커서를_만든다() {
    // given
    UUID requesterId = UUID.randomUUID();
    User owner = createUser(UUID.randomUUID());
    Playlist first = createPlaylist(owner, UUID.randomUUID(),
        LocalDateTime.of(2026, 10, 3, 12, 30, 15), 0);
    Playlist second = createPlaylist(owner, UUID.randomUUID(),
        LocalDateTime.of(2026, 10, 2, 12, 30, 15), 0);
    Playlist extra = createPlaylist(owner, UUID.randomUUID(),
        LocalDateTime.of(2026, 10, 1, 12, 30, 15), 0);
    PlaylistSearchRequest request =
        createSearchRequest(2, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of(first, second, extra));
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(3L);
    given(subscriptionRepository.findSubscribedPlaylistIds(eq(requesterId), anyCollection()))
        .willReturn(List.of());

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    assertThat(response.data())
        .extracting(PlaylistResponse::id)
        .containsExactly(first.getId(), second.getId());
    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("2026-10-02T12:30:15");
    assertThat(response.nextIdAfter()).isEqualTo(second.getId());
    assertThat(response.totalCount()).isEqualTo(3L);
  }


  @Test
  @DisplayName("정렬 기준이 subscribeCount이면 다음 커서는 구독자 수 문자열이다")
  void 정렬_기준이_subscribeCount이면_다음_커서는_구독자_수_문자열이다() {
    // given
    UUID requesterId = UUID.randomUUID();
    User owner = createUser(UUID.randomUUID());
    Playlist first = createPlaylist(owner, UUID.randomUUID(),
        LocalDateTime.of(2026, 10, 1, 0, 0), 30);
    Playlist extra = createPlaylist(owner, UUID.randomUUID(),
        LocalDateTime.of(2026, 10, 1, 0, 0), 5);
    PlaylistSearchRequest request =
        createSearchRequest(1, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of(first, extra));
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(2L);
    given(subscriptionRepository.findSubscribedPlaylistIds(eq(requesterId), anyCollection()))
        .willReturn(List.of());

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("30");
    assertThat(response.nextIdAfter()).isEqualTo(first.getId());
  }


  @Test
  @DisplayName("요청자가 구독한 플레이리스트만 subscribedByMe가 true이고 구독 조회는 한 번만 한다")
  void 요청자가_구독한_플레이리스트만_subscribedByMe가_true이다() {
    // given
    UUID requesterId = UUID.randomUUID();
    User owner = createUser(UUID.randomUUID());
    Playlist subscribed1 = createPlaylist(owner, UUID.randomUUID());
    Playlist notSubscribed = createPlaylist(owner, UUID.randomUUID());
    Playlist subscribed2 = createPlaylist(owner, UUID.randomUUID());
    PlaylistSearchRequest request =
        createSearchRequest(3, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of(subscribed1, notSubscribed, subscribed2));
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(3L);
    given(subscriptionRepository.findSubscribedPlaylistIds(eq(requesterId), anyCollection()))
        .willReturn(List.of(subscribed1.getId(), subscribed2.getId()));

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    assertThat(response.data())
        .extracting(PlaylistResponse::subscribedByMe)
        .containsExactly(true, false, true);
    verify(subscriptionRepository, times(1)).findSubscribedPlaylistIds(
        eq(requesterId),
        argThat(ids -> ids.size() == 3
            && ids.containsAll(List.of(
            subscribed1.getId(), notSubscribed.getId(), subscribed2.getId()))));
  }


  @Test
  @DisplayName("조회 결과가 없으면 빈 응답을 돌려주고 구독 조회는 하지 않는다")
  void 조회_결과가_없으면_빈_응답을_돌려주고_구독_조회는_하지_않는다() {
    // given
    UUID requesterId = UUID.randomUUID();
    PlaylistSearchRequest request =
        createSearchRequest(10, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of());
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(0L);

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    assertThat(response.data()).isEmpty();
    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextCursor()).isNull();
    assertThat(response.nextIdAfter()).isNull();
    assertThat(response.totalCount()).isZero();
    verifyNoInteractions(subscriptionRepository);
  }


  @Test
  @DisplayName("응답에는 요청한 정렬 기준과 방향이 응답 형식으로 담긴다")
  void 응답에는_요청한_정렬_기준과_방향이_담긴다() {
    // given
    UUID requesterId = UUID.randomUUID();
    PlaylistSearchRequest request =
        createSearchRequest(10, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.ASCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of());
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(0L);

    // when
    CursorResponse<PlaylistResponse> response = playlistService.getPlaylists(request, requesterId);

    // then
    assertThat(response.sortBy()).isEqualTo("subscribeCount");
    assertThat(response.sortDirection()).isEqualTo(SortDirection.ASCENDING);
  }


  @Test
  @DisplayName("요청 값을 검색 조건으로 변환해 검색과 개수 조회에 전달한다")
  void 요청_값을_검색_조건으로_변환해_전달한다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        "rock", ownerId, subscriberId,
        "2026-10-02T12:30:15", idAfter,
        5, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);
    PlaylistSearchCondition expected = new PlaylistSearchCondition(
        "rock", ownerId, subscriberId,
        "2026-10-02T12:30:15", idAfter,
        5, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    given(playlistRepository.search(any(PlaylistSearchCondition.class)))
        .willReturn(List.of());
    given(playlistRepository.count(any(PlaylistSearchCondition.class)))
        .willReturn(0L);

    // when
    playlistService.getPlaylists(request, requesterId);

    // then
    verify(playlistRepository).search(expected);
    verify(playlistRepository).count(expected);
  }

  @Test
  @DisplayName("소유자가 title과 description을 모두 수정하면 응답에 둘 다 반영된다")
  void 소유자가_제목과_설명을_수정하면_응답에_반영된다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));

    // when
    PlaylistResponse response = playlistService.updatePlaylist(playlistId, request, ownerId);

    // then
    assertThat(response.id()).isEqualTo(playlistId);
    assertThat(response.title()).isEqualTo("새 제목");
    assertThat(response.description()).isEqualTo("새 설명");
    assertThat(response.owner().userId()).isEqualTo(ownerId);
  }

  @Test
  @DisplayName("title만 수정하면 description은 그대로이다")
  void 제목만_수정하면_설명은_그대로이다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", null);

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));

    // when
    PlaylistResponse response = playlistService.updatePlaylist(playlistId, request, ownerId);

    // then
    assertThat(response.title()).isEqualTo("새 제목");
    assertThat(response.description()).isEqualTo("description");
  }

  @Test
  @DisplayName("description만 수정하면 title은 그대로이다")
  void 설명만_수정하면_제목은_그대로이다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(null, "새 설명");

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));

    // when
    PlaylistResponse response = playlistService.updatePlaylist(playlistId, request, ownerId);

    // then
    assertThat(response.title()).isEqualTo("title");
    assertThat(response.description()).isEqualTo("새 설명");
  }

  @Test
  @DisplayName("없는 플레이리스트를 수정하면 PlayListNotFoundException을 던진다")
  void 없는_플레이리스트_수정시_PlayListNotFoundException을_던진다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(PlayListNotFoundException.class)
        .isThrownBy(() -> playlistService.updatePlaylist(playlistId, request, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId));
  }

  @Test
  @DisplayName("소유자가 아니면 PlayListForbiddenException을 던지고 값은 바뀌지 않는다")
  void 소유자가_아니면_PlayListForbiddenException을_던지고_값은_바뀌지_않는다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");

    given(playlistRepository.findWithOwnerById(playlistId))
        .willReturn(Optional.of(playlist));

    // when & then
    assertThatExceptionOfType(PlayListForbiddenException.class)
        .isThrownBy(() -> playlistService.updatePlaylist(playlistId, request, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId)
            .containsEntry("requesterId", requesterId));

    assertThat(playlist.getTitle()).isEqualTo("title");
    assertThat(playlist.getDescription()).isEqualTo("description");
  }

  @Test
  @DisplayName("소유자가 삭제하면 구독, 콘텐츠 연결, 플레이리스트 순서로 삭제한다")
  void 소유자가_삭제하면_자식을_먼저_지우고_플레이리스트를_삭제한다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

    // when
    playlistService.deletePlaylist(playlistId, ownerId);

    // then
    InOrder inOrder = inOrder(subscriptionRepository, playlistContentRepository, playlistRepository);
    inOrder.verify(subscriptionRepository).deleteAllByPlaylistId(playlistId);
    inOrder.verify(playlistContentRepository).deleteAllByPlaylistId(playlistId);
    inOrder.verify(playlistRepository).delete(playlist);
  }

  @Test
  @DisplayName("없는 플레이리스트를 삭제하면 PlayListNotFoundException을 던지고 아무것도 삭제하지 않는다")
  void 없는_플레이리스트_삭제시_PlayListNotFoundException을_던진다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(PlayListNotFoundException.class)
        .isThrownBy(() -> playlistService.deletePlaylist(playlistId, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId));

    verifyNoInteractions(subscriptionRepository, playlistContentRepository);
    verify(playlistRepository, never()).delete(any(Playlist.class));
  }

  @Test
  @DisplayName("소유자가 아니면 PlayListForbiddenException을 던지고 아무것도 삭제하지 않는다")
  void 소유자가_아니면_삭제할_수_없다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

    // when & then
    assertThatExceptionOfType(PlayListForbiddenException.class)
        .isThrownBy(() -> playlistService.deletePlaylist(playlistId, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId)
            .containsEntry("requesterId", requesterId));

    verifyNoInteractions(subscriptionRepository, playlistContentRepository);
    verify(playlistRepository, never()).delete(any(Playlist.class));
  }

  @Test
  @DisplayName("소유자가 존재하는 콘텐츠를 추가하면 연결을 저장하고 플레이리스트 수정일을 갱신한다")
  void 소유자는_플레이리스트에_콘텐츠를_추가할_수_있다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);

    given(playlistRepository.findById(playlistId))
        .willReturn(Optional.of(playlist));
    given(contentRepository.existsById(contentId))
        .willReturn(true);
    given(playlistContentRepository
        .existsByPlaylistIdAndContentId(playlistId, contentId)).willReturn(false);

    // when
    playlistService.addContent(playlistId, contentId, ownerId);

    // then
    ArgumentCaptor<PlaylistContent> captor = ArgumentCaptor.forClass(PlaylistContent.class);
    verify(playlistContentRepository).save(captor.capture());
    assertThat(captor.getValue().getPlaylistId()).isEqualTo(playlistId);
    assertThat(captor.getValue().getContentId()).isEqualTo(contentId);
    verify(playlistRepository)
        .touchUpdatedAt(eq(playlistId), any(LocalDateTime.class));
  }

  @Test
  @DisplayName("없는 플레이리스트에 콘텐츠를 추가하면 PlayListNotFoundException을 던지고 아무것도 저장하지 않는다")
  void 없는_플레이리스트에_콘텐츠_추가시_PlayListNotFoundException을_던진다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(PlayListNotFoundException.class)
        .isThrownBy(() -> playlistService.addContent(playlistId, contentId, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId));

    verifyNoInteractions(contentRepository, playlistContentRepository);
    verify(playlistRepository, never()).touchUpdatedAt(any(), any());
  }


  @Test
  @DisplayName("없는 콘텐츠를 추가하면 ContentNotFoundException을 던지고 아무것도 저장하지 않는다")
  void 없는_콘텐츠를_추가하면_ContentNotFoundException을_던진다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(contentRepository.existsById(contentId)).willReturn(false);

    // when & then
    assertThatExceptionOfType(ContentNotFoundException.class)
        .isThrownBy(() -> playlistService.addContent(playlistId, contentId, ownerId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("contentId", contentId));

    verifyNoInteractions(playlistContentRepository);
    verify(playlistRepository, never()).touchUpdatedAt(any(), any());
  }

  @Test
  @DisplayName("이미 담긴 콘텐츠를 추가하면 PlayListContentDuplicatedException을 던지고 저장하지 않는다")
  void 이미_담긴_콘텐츠를_추가하면_PlayListContentDuplicatedException을_던진다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(contentRepository.existsById(contentId)).willReturn(true);
    given(playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId))
        .willReturn(true);

    // when & then
    assertThatExceptionOfType(PlayListContentDuplicatedException.class)
        .isThrownBy(() -> playlistService.addContent(playlistId, contentId, ownerId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId)
            .containsEntry("contentId", contentId));

    verify(playlistContentRepository, never()).save(any(PlaylistContent.class));
    verify(playlistRepository, never()).touchUpdatedAt(any(), any());
  }

  @Test
  @DisplayName("소유자가 담긴 콘텐츠를 삭제하면 연결을 삭제하고 플레이리스트 수정일을 갱신한다")
  void 소유자는_플레이리스트에서_콘텐츠를_삭제할_수_있다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(playlistContentRepository.deleteByPlaylistIdAndContentId(playlistId, contentId))
        .willReturn(1);

    // when
    playlistService.removeContent(playlistId, contentId, ownerId);

    // then
    verify(playlistContentRepository).deleteByPlaylistIdAndContentId(playlistId, contentId);
    verify(playlistRepository).touchUpdatedAt(eq(playlistId), any(LocalDateTime.class));
  }

  @Test
  @DisplayName("없는 플레이리스트에서 콘텐츠를 삭제하면 PlayListNotFoundException을 던지고 아무것도 삭제하지 않는다")
  void 없는_플레이리스트에서_콘텐츠_삭제시_PlayListNotFoundException을_던진다() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

    // when & then
    assertThatExceptionOfType(PlayListNotFoundException.class)
        .isThrownBy(() -> playlistService.removeContent(playlistId, contentId, requesterId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId));

    verifyNoInteractions(playlistContentRepository);
    verify(playlistRepository, never()).touchUpdatedAt(any(), any());
  }


  @Test
  @DisplayName("담기지 않은 콘텐츠를 삭제하면 PlayListContentNotFoundException을 던지고 수정일을 갱신하지 않는다")
  void 담기지_않은_콘텐츠를_삭제하면_PlayListContentNotFoundException을_던진다() {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Playlist playlist = createPlaylist(createUser(ownerId), playlistId);
    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(playlistContentRepository.deleteByPlaylistIdAndContentId(playlistId, contentId))
        .willReturn(0);

    // when & then
    assertThatExceptionOfType(PlayListContentNotFoundException.class)
        .isThrownBy(() -> playlistService.removeContent(playlistId, contentId, ownerId))
        .satisfies(e -> assertThat(e.getDetails())
            .containsEntry("playlistId", playlistId)
            .containsEntry("contentId", contentId));

    verify(playlistRepository, never()).touchUpdatedAt(any(), any());
  }



  //-----------[헬퍼 메서드]
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
    Playlist playlist = new Playlist(owner, "title", "description");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    ReflectionTestUtils.setField(playlist, "subscriberCount", 3L);
    ReflectionTestUtils.setField(
        playlist,
        "updatedAt",
        LocalDateTime.of(2026, 10, 26, 12, 0)
    );
    return playlist;
  }

  private PlaylistSearchRequest createSearchRequest(
      int limit, PlaylistSortBy sortBy, SortDirection sortDirection) {
    return new PlaylistSearchRequest(
        null, null, null, null, null,
        limit, sortBy, sortDirection);
  }

  private Playlist createPlaylist(
      User owner, UUID id, LocalDateTime updatedAt, long subscriberCount) {
    Playlist playlist = createPlaylist(owner, id);
    ReflectionTestUtils.setField(playlist, "updatedAt", updatedAt);
    ReflectionTestUtils.setField(playlist, "subscriberCount", subscriberCount);
    return playlist;
  }
}