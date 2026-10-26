package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.global.validation.Utf8ByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

        @NotBlank(message = "이메일은 필수 입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        String password
) {
        public UserCreateCommand toCommand() {
                return new UserCreateCommand(email, name, password);
        }

}
