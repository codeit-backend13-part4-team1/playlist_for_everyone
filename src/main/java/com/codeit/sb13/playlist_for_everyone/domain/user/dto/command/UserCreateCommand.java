package com.codeit.sb13.playlist_for_everyone.domain.user.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;

public record UserCreateCommand(
        String email,
        String name,
        String password,
        Role role

) {
}
