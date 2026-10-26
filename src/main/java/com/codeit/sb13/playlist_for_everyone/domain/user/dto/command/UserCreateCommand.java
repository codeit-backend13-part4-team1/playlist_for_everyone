package com.codeit.sb13.playlist_for_everyone.domain.user.dto.command;

public record UserCreateCommand(
        String email,
        String name,
        String password
) {
}
