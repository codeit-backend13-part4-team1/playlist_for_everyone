package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone;

public record FollowResult(FollowDto followDto, boolean created) {

    public static FollowResult alreadyFollowed(FollowDto followDto){
        return new FollowResult(followDto, false);
    }

    public static FollowResult followCreated(FollowDto followDto){
        return new FollowResult(followDto, true);
    }
}
