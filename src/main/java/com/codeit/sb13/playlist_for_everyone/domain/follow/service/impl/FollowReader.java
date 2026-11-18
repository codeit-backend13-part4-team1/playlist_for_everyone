package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 팔로우 조회와 DTO 변환을 읽기 전용 트랜잭션 안에서 수행하는 컴포넌트.
 * 서비스의 등록·조회 기능이 같은 관계 조회를 재사용한다.
 *
 * <p>대상 사용자의 존재 여부를 별도로 확인하지 않는다. 관계 조회의 Optional,
 * 팔로워 수 0, 빈 ID 목록을 각 서비스 계약에 맞게 사용한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FollowReader {

    private final FollowRepository followRepository;

    public Optional<FollowResult> findExistingFollow(FollowCommand command){
        // 대상 ID만으로 조회하면 다른 사용자의 관계가 섞이므로 요청자 ID도 조건에 포함한다.
        return followRepository.findByFolloweeIdAndFollowerId(command.followeeId(), command.followerId())
                .map(FollowDto::from)
                .map(FollowResult::alreadyFollowed);
    }

    public Long countFollowers(UUID followeeId){
        return followRepository.countByFolloweeId(followeeId);
    }

    public List<UUID> findFollowerIds(UUID followeeId){
        return followRepository.findFollowerIdsByFolloweeId(followeeId);
    }
}
