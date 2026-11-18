package com.codeit.sb13.playlist_for_everyone.domain.follow.service;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;

import java.util.List;
import java.util.UUID;

/**
 * 팔로우 등록·취소·조회와 다른 도메인에서 사용할 팔로워 ID 조회 계약.
 *
 * <p>HTTP 요청의 인증은 공통 보안 필터가 담당하고, 요청자 ID는 컨트롤러가
 * Principal에서 추출해 Command에 넣는다. 내부 호출자도 신뢰할 수 있는 요청자 ID를 전달해야 한다.
 * 취소 소유자 인가는 구현체의 메서드 보안과 FollowGuard가 담당한다.
 */
public interface FollowService {

    /**
     * 요청자가 대상을 팔로우한다. 자기 팔로우는 거부하며, 중복 요청은 기존 관계를 반환한다.
     *
     * @return 신규 생성 여부와 관계 정보. 컨트롤러는 created 값으로 201과 200을 구분한다.
     */
    FollowResult follow(FollowCommand command);

    /**
     * 요청자가 소유한 관계를 삭제한다. 이미 없어진 관계는 정상 종료한다.
     * 존재하는 타인 관계는 Spring 메서드 보안이 서비스 본문 실행 전에 거부한다.
     */
    void unfollow(UnFollowCommand command);

    /** 요청자와 대상의 관계를 조회한다. 관계가 없으면 FollowNotFoundException이 발생한다. */
    FollowDto findFollowDto(FollowCommand command);

    /** 대상의 팔로워 수를 반환한다. 대상 사용자가 없거나 관계가 없으면 0이다. */
    Long countFollowers(UUID followeeId);

    /**
     * 대상 사용자를 팔로우하는 사용자 ID 목록을 반환한다. 관계가 없으면 빈 목록이다.
     * 다른 도메인에서 사용할 내부 조회이며, 현재 인증 사용자나 공개 HTTP API에 의존하지 않는다.
     * 반환 순서는 보장하지 않는다.
     */
    List<UUID> findFollowerIds(UUID followeeId);
}
