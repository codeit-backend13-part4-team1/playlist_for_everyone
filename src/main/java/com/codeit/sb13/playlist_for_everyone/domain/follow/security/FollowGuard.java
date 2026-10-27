package com.codeit.sb13.playlist_for_everyone.domain.follow.security;

import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FollowGuard {

    private final FollowRepository followRepository;

    public boolean canUnfollow(UUID followerId, UUID followId){
        return followRepository.findById(followId)
                .map(follow -> follow.getFollowerId().equals(followerId))
                .orElse(true);
    }

}
