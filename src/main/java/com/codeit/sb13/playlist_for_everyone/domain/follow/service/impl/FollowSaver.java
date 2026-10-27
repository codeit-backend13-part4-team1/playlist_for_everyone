package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FollowSaver {

    private final FollowRepository followRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FollowDto save(Follow follow){
        return FollowDto.from(followRepository.save(follow));
    }

}
