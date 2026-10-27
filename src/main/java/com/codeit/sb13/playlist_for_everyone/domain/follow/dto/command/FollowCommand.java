package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command;

import java.util.UUID;

public record FollowCommand(UUID followeeId, UUID followerId) {
}
