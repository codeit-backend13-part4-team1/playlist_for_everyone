package com.codeit.sb13.playlist_for_everyone.domain.user.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserDto(
        UUID id,
        LocalDateTime createdAt,
        String email,
        String name,
        String profileImageUrl,
        Role role,
        boolean locked
) {
}
