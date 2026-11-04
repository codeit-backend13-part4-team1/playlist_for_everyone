package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;

import java.util.UUID;

/**
 * 등록·내 관계 조회의 공통 응답. id는 두 사용자 중 한 명의 ID가 아닌 관계 자체의 ID다.
 * 엔티티와 달리 생성 시각이나 사용자 이름은 응답 필드에 포함하지 않는다.
 */
public record FollowDto(UUID id, UUID followeeId, UUID followerId) {


    public static FollowDto from(Follow follow){
        return new FollowDto(follow.getId(), follow.getFolloweeId(), follow.getFollowerId());
    }
}
