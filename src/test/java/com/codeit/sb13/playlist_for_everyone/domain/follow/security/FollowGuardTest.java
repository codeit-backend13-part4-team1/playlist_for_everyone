package com.codeit.sb13.playlist_for_everyone.domain.follow.security;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 저장소를 Mock으로 대체해 본인·타인·없는 관계에 대한 Guard의 판단을 확인한다.
 * JWT 인증과 메서드 보안 프록시의 실행 여부는 이 테스트 범위에 포함하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 접근 권한 검사")
class FollowGuardTest {

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private FollowGuard followGuard;

    @Test
    @DisplayName("본인의 팔로우 관계이면 취소를 허용한다.")
    void canUnfollow_allowsOwner(){
        UUID followerId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        Follow follow = mock(Follow.class);
        given(followRepository.findById(followId)).willReturn(Optional.of(follow));
        given(follow.getFollowerId()).willReturn(followerId);

        boolean allowed = followGuard.canUnfollow(followerId, followId);

        assertThat(allowed).isTrue();
        verify(followRepository).findById(followId);
    }

    @Test
    @DisplayName("타인의 팔로우 관계이면 취소를 거부한다.")
    void canUnfollow_deniesOtherUser(){
        UUID followId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Follow follow = mock(Follow.class);

        given(followRepository.findById(followId)).willReturn(Optional.of(follow));
        given(follow.getFollowerId()).willReturn(ownerId);
        boolean allowed = followGuard.canUnfollow(requesterId, followId);
        assertThat(allowed).isFalse();
        verify(followRepository).findById(followId);
    }

    @Test
    @DisplayName("팔로우 관계가 없으면 취소를 허용한다.")
    void canUnfollow_allowsMissingFollow(){
        UUID followerId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        given(followRepository.findById(followId)).willReturn(Optional.empty());

        boolean allowed = followGuard.canUnfollow(followerId, followId);
        assertThat(allowed).isTrue();

        verify(followRepository).findById(followId);
    }

}
