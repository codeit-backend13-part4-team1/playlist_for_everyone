package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Refresh Token 쿠키의 발급과 만료를 한곳에서 만든다.
 */
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "REFRESH_TOKEN";

    public RefreshTokenCookieFactory(Duration validity, boolean secure) {

    }

    public ResponseCookie create(String refreshToken) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }

    public ResponseCookie expire() {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }
}
