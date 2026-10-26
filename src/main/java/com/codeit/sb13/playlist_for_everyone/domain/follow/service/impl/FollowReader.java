package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FollowReader {

    private final FollowRepository followRepository;

    public Optional<FollowResult> findExistingFollow(FollowCommand command){
        return followRepository.findByFolloweeIdAndFollowerId(command.followeeId(), command.followerId())
                .map(FollowDto::from)
                .map(FollowResult::alreadyFollowed);
    }

    public Long countFollowers(UUID followeeId){
        return followRepository.countByFolloweeId(followeeId);
    }
}
