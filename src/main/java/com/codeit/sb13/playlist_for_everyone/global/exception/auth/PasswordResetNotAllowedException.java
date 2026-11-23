package com.codeit.sb13.playlist_for_everyone.global.exception.auth;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

/**
 * 비밀번호 없이 소셜 로그인으로 가입한 계정은 임시 비밀번호를 발급하지 않는다.
 */
public class PasswordResetNotAllowedException extends AuthException{

    public PasswordResetNotAllowedException() {
        super(ApiErrorCode.PASSWORD_RESET_NOT_ALLOWED, Map.of());
    }
}
