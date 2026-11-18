package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * true는 잠금, false는 잠금 해제.
 * Boolean으로 선언하여 값 누락과 false를 구분한다.
 */
public record UserLockUpdateRequest(
        @NotNull(message = "잠금 상태는 필수입니다.")
        Boolean locked
) {
}
