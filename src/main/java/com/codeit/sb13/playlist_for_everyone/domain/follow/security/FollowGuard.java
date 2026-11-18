package com.codeit.sb13.playlist_for_everyone.domain.follow.security;

import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 팔로우 취소의 소유자 인가 조건.
 *
 * <p>JWT 검증이나 SecurityContext 조회는 수행하지 않는다. 컨트롤러가 인증된 요청자에서 만든
 * Command의 followerId를 받아 관계 소유자와 비교한다.
 */
@Component
@RequiredArgsConstructor
public class FollowGuard {

    private final FollowRepository followRepository;

    /**
     * 본인 관계는 허용하고 타인 관계는 거부한다.
     * 이미 없는 관계도 허용해 서비스가 정상 종료하고 HTTP 204를 반환할 수 있게 한다.
     */
    public boolean canUnfollow(UUID followerId, UUID followId){
        return followRepository.findById(followId)
                .map(follow -> follow.getFollowerId().equals(followerId))
                .orElse(true);
    }

}
