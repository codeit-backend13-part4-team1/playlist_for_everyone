package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FollowSaver {

    private final FollowRepository followRepository;
    private final FollowUserReader followUserReader;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FollowDto save(FollowCommand command){
        Follow saved = followRepository.save(createFollow(command));
        eventPublisher.publishEvent(new UserFollowedEvent(saved.getFollowerId(), saved.getFollower().getName(), saved.getFolloweeId()));
        return FollowDto.from(saved);
    }

    private Follow createFollow(FollowCommand command) {
        return new Follow(getUser(command.followerId()), getFollowee(command.followeeId()));
    }

    private User getFollowee(UUID userId) {
        try {
            return getUser(userId);
        }catch (UserNotFoundException e){
            throw new FollowTargetNotFoundException(Map.of("followeeId", userId));
        }
    }

    private User getUser(UUID userId) {
        return followUserReader.getById(userId);
    }

}
