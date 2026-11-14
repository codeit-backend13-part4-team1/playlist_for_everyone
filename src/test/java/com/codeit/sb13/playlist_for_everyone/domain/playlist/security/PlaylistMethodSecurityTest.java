package com.codeit.sb13.playlist_for_everyone.domain.playlist.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl.PlaylistContentSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl.PlaylistReader;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl.PlaylistServiceImpl;

import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl.PlaylistWriter;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(PlaylistMethodSecurityTest.TestConfig.class)
@DisplayName("플레이리스트 메서드 인가")
class PlaylistMethodSecurityTest {

  @Autowired
  private PlaylistService playlistService;

  @MockitoBean(name = "playlistGuard")
  private PlaylistGuard playlistGuard;

  @MockitoBean
  private PlaylistRepository playlistRepository;

  @MockitoBean
  private PlaylistContentRepository playlistContentRepository;

  @MockitoBean
  private ContentRepository contentRepository;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private SubscriptionRepository subscriptionRepository;

  @MockitoBean
  private UserSummaryMapper summaryMapper;

  @MockitoBean
  private PlaylistReader playlistReader;

  @MockitoBean
  private PlaylistContentSummaryMapper playlistContentSummaryMapper;

  @MockitoBean
  private PlaylistWriter playlistWriter;

  @Test
  @WithMockUser
  @DisplayName("Guard가 거부하면 콘텐츠 추가 시 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
  void 가드가_거부하면__콘텐츠_추가가_거부() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistGuard.canModify(playlistId, requesterId)).willReturn(false);

    assertThatThrownBy(() -> playlistService.addContent(playlistId, contentId, requesterId))
        .isInstanceOf(AccessDeniedException.class);

    verify(playlistGuard).canModify(playlistId, requesterId);
    verifyNoInteractions(playlistRepository, userRepository, subscriptionRepository,
        playlistContentRepository, contentRepository);
  }

  @Test
  @WithMockUser
  @DisplayName("Guard가 거부하면 플레이리스트 수정 시 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
  void 가드가_거부할_경우_재생_목록_업데이트가_거부됨() {
    // given
      UUID playlistId = UUID.randomUUID();
      UUID requesterId = UUID.randomUUID();
      given(playlistGuard.canModify(playlistId, requesterId)).willReturn(false);

    // when & then
    assertThatThrownBy(() -> playlistService.updatePlaylist
        (playlistId, new PlaylistUpdateRequest("제목", "설명"), requesterId))
        .isInstanceOf(AccessDeniedException.class);

    verify(playlistGuard).canModify(playlistId, requesterId);
    verifyNoInteractions(playlistRepository, userRepository, subscriptionRepository,
        playlistContentRepository, contentRepository);


  }

  @Test
  @WithMockUser
  @DisplayName("Guard가 거부하면 플레이리스트 삭제 시 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
  void 가드가_거부할_경우_재생_목록_삭제가_거부됨() {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistGuard.canModify(playlistId, requesterId)).willReturn(false);


    // when & then
    assertThatThrownBy(() -> playlistService.deletePlaylist(playlistId, requesterId))
        .isInstanceOf(AccessDeniedException.class);

    verify(playlistGuard).canModify(playlistId, requesterId);
    verifyNoInteractions(playlistRepository, userRepository, subscriptionRepository,
        playlistContentRepository, contentRepository);

  }

  @Test
  @WithMockUser
  @DisplayName("Guard가 거부하면 콘텐츠 삭제 시 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
  void 콘텐츠_삭제_시_가드가_거부하면_거부됨() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistGuard.canModify(playlistId, requesterId)).willReturn(false);

    assertThatThrownBy(() -> playlistService.removeContent(playlistId, contentId, requesterId))
        .isInstanceOf(AccessDeniedException.class);

    verify(playlistGuard).canModify(playlistId, requesterId);
    verifyNoInteractions(playlistRepository, userRepository, subscriptionRepository,
        playlistContentRepository, contentRepository);
  }

  @Test
  @DisplayName("인증 정보가 없으면 인증 예외가 발생하고 Guard와 서비스를 실행하지 않는다")
  void 콘텐츠_추가_시_인증_정보가_없으면_거부됨() {
    assertThatThrownBy(() -> playlistService.addContent(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

    verifyNoInteractions(playlistGuard, playlistRepository, userRepository,
        subscriptionRepository, playlistContentRepository, contentRepository);
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableMethodSecurity
  @Import(PlaylistServiceImpl.class)
  static class TestConfig {
  }
}