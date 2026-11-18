package com.codeit.sb13.playlist_for_everyone.global.exception.auth;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

/**
 * 같은 계정의 비밀번호 초기화 요청이 제한을 넘었다.
 * details.retryAfterSeconds: 다시 요청할 수 있을 때까지 남은 초
 */
public class PasswordResetTooManyRequestsException extends AuthException{

    public PasswordResetTooManyRequestsException(long retryAfterSeconds) {
        super(ApiErrorCode.PASSWORD_RESET_TOO_MANY_REQUESTS,
                Map.of("retryAfterSeconds", retryAfterSeconds));
    }
}
