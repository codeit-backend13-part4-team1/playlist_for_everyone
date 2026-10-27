package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;

import java.util.UUID;

public record FollowDto(UUID id, UUID followeeId, UUID followerId) {


    public static FollowDto from(Follow follow){
        return new FollowDto(follow.getId(), follow.getFolloweeId(), follow.getFollowerId());
    }
}
