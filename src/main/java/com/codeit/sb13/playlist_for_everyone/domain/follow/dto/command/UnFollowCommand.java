package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command;

import java.util.UUID;

public record UnFollowCommand(UUID followId, UUID followerId) {
}
