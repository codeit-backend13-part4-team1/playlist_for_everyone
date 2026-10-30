package com.codeit.sb13.playlist_for_everyone.domain.playlist.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.SubscriptionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl.PlaylistServiceImpl;

import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
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

  @Test
  @WithMockUser
  @DisplayName("Guard가 거부하면 콘텐츠 추가 시 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
  void addContent_rejectsWhenGuardDenies() {
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
  @DisplayName("Guard가 거부하면 콘텐츠 삭제 시 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
  void removeContent_rejectsWhenGuardDenies() {
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
  void addContent_rejectsWhenAuthenticationMissing() {
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