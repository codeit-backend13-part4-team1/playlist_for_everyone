package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;

import java.util.UUID;

public record FollowRequest(UUID followeeId) {

    public FollowCommand toFollowCommand(UUID followerId) {
        return new FollowCommand(followeeId, followerId);
    }
}
