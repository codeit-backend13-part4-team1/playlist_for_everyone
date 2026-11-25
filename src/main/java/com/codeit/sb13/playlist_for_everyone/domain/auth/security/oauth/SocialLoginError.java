package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

/**
 * 소셜 로그인 실패 사유.
 * 로그인 화면 리다이렉트의 error_message 쿼리 파라미터로 전달한다.
 */
public enum SocialLoginError {

    EMAIL_ALREADY_EXISTS("email_already_exists"),
    EMAIL_NOT_VERIFIED("email_not_verified"),
    ACCOUNT_LOCKED("account_locked"),
    ACCESS_DENIED("access_denied"),
    LOGIN_FAILED("login_failed");

    private final String code;

    SocialLoginError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

}
