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

/**
 * 사용자 조회, 신규 관계 저장, 생성 이벤트 발행을 하나의 독립 트랜잭션에서 수행한다.
 *
 * <p>서비스와 별도 빈으로 두어 Spring 트랜잭션 프록시를 거치게 한다.
 * 저장 실패를 호출자가 잡았을 때 실패한 트랜잭션에서 재조회하지 않도록 경계를 분리한 것이다.
 */
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
        // save 호출이나 이벤트 발행 자체가 커밋 완료를 의미하지 않는다. 명시적 flush는 강제하지 않는다.
        // 커밋된 관계에만 후속 처리가 필요하면 수신 측에서 AFTER_COMMIT 리스너를 사용한다.
        // 이름은 이 트랜잭션에서 조회한 사용자 값이며 Principal/JWT의 이름으로 대체하지 않는다.
        eventPublisher.publishEvent(new UserFollowedEvent(saved.getFollowerId(), saved.getFollowerName(), saved.getFolloweeId()));
        return FollowDto.from(saved);
    }

    // 엔티티를 외부에서 전달받지 않고, 현재 저장 트랜잭션에서 두 사용자를 조회한다.
    private Follow createFollow(FollowCommand command) {
        return new Follow(getUser(command.followerId()), getFollowee(command.followeeId()));
    }

    private User getFollowee(UUID userId) {
        try {
            return getUser(userId);
        }catch (UserNotFoundException e){
            // 대상 미존재는 팔로우 요청 오류로 변환한다. 요청자 미존재 예외는 getUser에서 그대로 전달된다.
            throw new FollowTargetNotFoundException(Map.of("followeeId", userId));
        }
    }

    private User getUser(UUID userId) {
        return followUserReader.getById(userId);
    }

}
