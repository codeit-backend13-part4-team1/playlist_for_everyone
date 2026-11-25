package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import org.springframework.security.core.AuthenticationException;

import java.util.Objects;

/**
 * 소셜 로그인 정책에 따라 로그인을 거부할 때 사용한다.
 * 실패 핸들러가 사유 코드를 로그인 화면으로 전달한다.
 */
public class SocialLoginException extends AuthenticationException {

    private final SocialLoginError error;

    public SocialLoginException(SocialLoginError error) {
        super(Objects.requireNonNull(error).code());
        this.error = error;
    }

    public SocialLoginException(SocialLoginError error, Throwable cause) {
        super(Objects.requireNonNull(error).code(), cause);
        this.error = error;
    }

    public SocialLoginError getError() {
        return error;
    }


}
