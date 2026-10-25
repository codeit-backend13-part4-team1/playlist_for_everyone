package com.codeit.sb13.playlist_for_everyone.domain.follow.security;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl.FollowSaver;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl.FollowServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(FollowMethodSecurityTest.TestConfig.class)
@DisplayName("팔로우 메서드 인가")
public class FollowMethodSecurityTest {

    @Autowired
    private FollowService followService;

    @MockitoBean(name = "followGuard")
    private FollowGuard followGuard;

    @MockitoBean
    private FollowRepository followRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FollowSaver followSaver;

    @Test
    @WithMockUser
    @DisplayName("Guard가 취소를 거부하면 접근 거부 예외가 발생하고 서비스를 실행하지 않는다")
    void unfollow_rejectsWhenGuardDenies(){
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        UnFollowCommand command = new UnFollowCommand(followId, followerId);

        given(followGuard.canUnfollow(followerId, followId)).willReturn(false);

        assertThatThrownBy(()-> followService.unfollow(command))
                .isInstanceOf(AccessDeniedException.class);

        verify(followGuard).canUnfollow(followerId, followId);
        verifyNoInteractions(followRepository, userRepository, followSaver);

    }

    @Test
    @WithMockUser
    @DisplayName("Guard가 취소를 허용하면 본인의 팔로우 관계를 삭제한다.")
    void unfollow_deletesOwnFollowWhenGuardAllows(){
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        UnFollowCommand command = new UnFollowCommand(followId, followerId);
        Follow follow = mock(Follow.class);

        given(followGuard.canUnfollow(followerId, followId)).willReturn(true);
        given(followRepository.findById(followId)).willReturn(Optional.of(follow));
        given(follow.getFollowerId()).willReturn(followerId);

        followService.unfollow(command);

        verify(followGuard).canUnfollow(followerId, followId);
        verify(followRepository).findById(followId);
        verify(followRepository).delete(follow);
        verifyNoInteractions(userRepository, followSaver);


    }

    @Test
    @DisplayName("인증 정보가 없으면 인증 예외가 발생하고 Guard와 서비스를 실행하지 않는다.")
    void unfollow_rejectsWhenAuthenticationMissing(){
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        UnFollowCommand command = new UnFollowCommand(followId, followerId);

        assertThatThrownBy(()-> followService.unfollow(command))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(
                followGuard,
                followRepository,
                userRepository,
                followSaver
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(FollowServiceImpl.class)
    static class TestConfig{

    }
}
