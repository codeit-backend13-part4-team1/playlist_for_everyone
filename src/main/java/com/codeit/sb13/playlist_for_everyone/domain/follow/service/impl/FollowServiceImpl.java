package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FollowServiceImpl implements FollowService {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String FOLLOW_UNIQUE_CONSTRAINT = "uq_follows_follower_followee";

    private final FollowRepository followRepository;
    private final FollowSaver followSaver;
    private final UserRepository userRepository;

    @Override
    public FollowResult follow(FollowCommand command) {
        return getFollowResult(command)
                .orElseGet(() -> save(command));
    }

    @Override
    public void unfollow(UnFollowCommand command) {

    }

    private Optional<FollowResult> getFollowResult(FollowCommand command) {
        return followRepository.findByFolloweeIdAndFollowerId(command.followeeId(), command.followerId())
                .map(follow -> FollowResult.alreadyFollowed(FollowDto.from(follow)));
    }

    private FollowResult save(FollowCommand command){
        User follower = getUser(command.followerId());
        User followee = getUser(command.followeeId());

        try {
            Follow follow = followSaver.save(new Follow(follower, followee));
            return FollowResult.followCreated(FollowDto.from(follow));
        }catch (DataIntegrityViolationException e){
            if (!isFollowUniqueViolation(e)) {
                throw e;
            }

            return getFollowResult(command)
                    .orElseThrow(() -> e);
        }
    }

    private boolean isFollowUniqueViolation(DataIntegrityViolationException e){
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                && UNIQUE_VIOLATION_SQL_STATE.equals(violation.getSQLState())
                && FOLLOW_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())
            ){
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(Map.of("userId", userId)));
    }
}
