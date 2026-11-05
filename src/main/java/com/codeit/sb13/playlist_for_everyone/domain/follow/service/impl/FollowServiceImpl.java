package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.SelfFollowNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 등록·취소·조회 정책을 조합하는 서비스.
 *
 * <p>등록 메서드 전체에 트랜잭션을 걸지 않고 조회와 저장을 별도 빈에 위임한다.
 * 일반 HTTP 호출에서는 저장 트랜잭션이 실패·종료된 뒤 기존 관계를 다시 조회할 수 있다.
 * 외부 트랜잭션에서 이 서비스를 호출한다면 저장은 독립 커밋되므로 호출자의 롤백과 함께 취소되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl implements FollowService {

    // PostgreSQL UNIQUE 위반 코드와 Flyway에 정의한 팔로우 제약조건을 함께 확인한다.
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String FOLLOW_UNIQUE_CONSTRAINT = "uq_follows_follower_followee";

    private final FollowRepository followRepository;
    private final FollowSaver followSaver;
    private final FollowReader followReader;

    @Override
    public FollowResult follow(FollowCommand command) {
        if (command.followerId().equals(command.followeeId())) {
            throw new SelfFollowNotAllowedException();
        }

        // 사전 조회는 반복 요청의 저장을 줄인다. 동시 요청의 중복 방지는 DB UNIQUE가 최종 담당한다.
        return followReader.findExistingFollow(command)
                .orElseGet(() -> save(command));
    }

    @Override
    // 인증 여부는 공통 HTTP 보안 필터에서 확인한다. 여기서는 Command의 요청자가 관계 소유자인지 판단한다.
    @PreAuthorize("@followGuard.canUnfollow(#command.followerId(), #command.followId())")
    @Transactional
    public void unfollow(UnFollowCommand command) {
        followRepository.findById(command.followId())
                .ifPresentOrElse(
                        follow -> {
                            // 실제 삭제 대상도 재확인한다. 타인 요청의 403은 이 분기보다 앞선 Guard 거부에서 발생한다.
                            if (!follow.getFollowerId().equals(command.followerId())) {
                                log.debug("팔로워 ID 가 일치하지 않습니다.: {} vs. {}", follow.getFollowerId(), command.followerId());
                                return;
                            }
                            followRepository.delete(follow);
                        },
                        () -> log.debug("팔로우를 찾을수 없습니다.: {}", command.followId())
                );
    }

    @Override
    public FollowDto findFollowDto(FollowCommand command) {
        return followReader.findExistingFollow(command)
                .map(FollowResult::followDto)
                .orElseThrow(() -> new FollowNotFoundException(Map.of("followeeId", command.followeeId())));
    }

    @Override
    public Long countFollowers(UUID followeeId) {
        return followReader.countFollowers(followeeId);
    }

    @Override
    public List<UUID> findFollowerIds(UUID followeeId) {
        return followReader.findFollowerIds(followeeId);
    }

    private FollowResult save(FollowCommand command){
        try {
            // 다른 빈의 프록시 호출이므로 REQUIRES_NEW의 커밋까지 끝나야 결과를 받는다.
            return FollowResult.followCreated(followSaver.save(command));
        } catch (DataIntegrityViolationException e){
            if (!isFollowUniqueViolation(e)) {
                throw new FollowCreationFailedException(e);
            }
            // 이 시점에는 실패한 저장 트랜잭션이 끝났다. 경쟁 요청이 만든 관계를 조회해 중복 응답으로 반환한다.
            // 일치하는 관계가 없으면 성공으로 간주하지 않고 생성 실패를 전달한다.
            return followReader.findExistingFollow(command)
                    .orElseThrow(() -> new FollowCreationFailedException(e));
        }
    }

    /**
     * Spring 예외에 감싸진 Hibernate 원인을 찾아 팔로우 중복만 식별한다.
     * FK·NOT NULL 위반이나 다른 UNIQUE 위반은 중복 팔로우의 정상 응답으로 바꾸지 않는다.
     */
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
}
