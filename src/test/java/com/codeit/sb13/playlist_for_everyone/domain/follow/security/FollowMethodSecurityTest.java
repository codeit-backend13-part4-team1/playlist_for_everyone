package com.codeit.sb13.playlist_for_everyone.domain.follow.security;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl.FollowReader;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl.FollowSaver;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl.FollowServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * 실제 Spring 메서드 보안 프록시가 Guard의 허용·거부 결과를 적용하는지 확인한다.
 * Guard와 저장소는 Mock이다. 서비스에는 인증 여부 검사 책임을 두지 않으며,
 * 미인증 HTTP 요청의 401 처리는 공통 보안 필터를 포함한 통합 테스트에서 검증한다.
 */
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

    @MockitoBean
    private FollowReader followReader;

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

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(FollowServiceImpl.class)
    static class TestConfig{

    }
}
