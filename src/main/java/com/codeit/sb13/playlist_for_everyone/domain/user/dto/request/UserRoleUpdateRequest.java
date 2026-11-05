package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자가 변경할 사용자 권한
 * Role enum에 없는 값은 JSON 변환 단계에서 거절된다.
 */
public record UserRoleUpdateRequest(
        @NotNull(message = "변경할 권한은 필수입니다.")
        Role role
) {
}
