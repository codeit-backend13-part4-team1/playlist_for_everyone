package com.codeit.sb13.playlist_for_everyone.domain.follow.event;

import java.util.UUID;

public record UserFollowedEvent(UUID followerId, String followerName, UUID followeeId) {
}
