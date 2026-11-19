package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Objects;

/**
 * Refresh Token 쿠키의 발급과 만료를 한곳에서 만든다.
 *
 * 브라우저는 이름·경로·도메인이 같은 쿠키만 덮어쓰거나 지운다.
 * 발급과 만료가 같은 설정을 쓰도록 쿠키 속성은 이 클래스에서만 정한다.
 */
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "REFRESH_TOKEN";

    static final String COOKIE_PATH = "/api/auth";

    private static final String SAME_SITE = "Lax";

    private final Duration validity;
    private final boolean secure;

    /**
     *
     * @param validity 브라우저의 쿠키 보관 기간. Refresh Token 유효기간과 같아야 한다.
     * @param secure HTTPS 환경에서는 true, 로컬 HTTP에서는 false
     */
    public RefreshTokenCookieFactory(Duration validity, boolean secure) {
        this.validity = Objects.requireNonNull(validity, "Refresh Token 쿠키 유효기간은 필수입니다.");
        if (validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 쿠키 유효기간은 양수여야 합니다.");
        }
        this.secure = secure;
    }

    /**
     * 로그인과 재발급에서 브라우저에 보관할 Refresh Token 쿠키를 만든다.
     */
    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(validity)
                .build();
    }

    /**
     * 로그아웃에서 기존 쿠키를 지우기 위한 만료 쿠키를 만든다.
     */
    public ResponseCookie expire() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH);
    }
}
