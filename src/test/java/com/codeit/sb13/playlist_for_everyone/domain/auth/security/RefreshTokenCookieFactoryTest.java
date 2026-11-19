package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Refresh Token 쿠키 생성")
class RefreshTokenCookieFactoryTest {

    private static final Duration VALIDITY = Duration.ofDays(7);

    @Test
    @DisplayName("발급 쿠키에 이름, 경로, 보안 속성과 유효기간을 설정한다.")
    void 발급_쿠키에_이름_경로_보안_속성과_유효기간을_설정한다() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(VALIDITY, true);

        ResponseCookie cookie = factory.create("refresh-token");

        assertThat(cookie.getName()).isEqualTo("REFRESH_TOKEN");
        assertThat(cookie.getValue()).isEqualTo("refresh-token");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(VALIDITY);
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    @DisplayName("로컬 HTTP 환경 설정이면 Secure 없이 발급한다.")
    void 로컬_HTTP_환경_설정이면_Secure_없이_발급한다() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(VALIDITY, false);

        assertThat(factory.create("refresh-token").isSecure()).isFalse();
    }

    @Test
    @DisplayName("만료 쿠키는 빈 값과 Max-Age 0으로 만든다.")
    void 만료_쿠키는_빈_값과_MaxAge_0으로_만든다() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(VALIDITY, true);

        ResponseCookie cookie = factory.expire();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("만료 쿠키는 발급 쿠키와 같은 이름, 경로, 보안 속성을 사용한다.")
    void 만료_쿠키는_발급_쿠키와_같은_이름_경로_보안_속성을_사용한다() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(VALIDITY, true);

        ResponseCookie issued = factory.create("refresh-token");
        ResponseCookie expired = factory.expire();

        // 이름·경로·도메인이 하나라도 다르면 브라우저가 기존 쿠키를 지우지 않는다.
        assertThat(expired.getName()).isEqualTo(issued.getName());
        assertThat(expired.getPath()).isEqualTo(issued.getPath());
        assertThat(expired.getDomain()).isEqualTo(issued.getDomain());
        assertThat(expired.isHttpOnly()).isEqualTo(issued.isHttpOnly());
        assertThat(expired.isSecure()).isEqualTo(issued.isSecure());
        assertThat(expired.getSameSite()).isEqualTo(issued.getSameSite());
    }

    @Test
    @DisplayName("유효기간이 없으면 생성할 수 없다.")
    void 유효기간이_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> new RefreshTokenCookieFactory(null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "유효기간={0}초")
    @ValueSource(longs = {0, -1})
    @DisplayName("유효기간이 0 이하이면 생성할 수 없다.")
    void 유효기간이_0_이하이면_생성할_수_없다(long seconds) {
        assertThatThrownBy(() -> new RefreshTokenCookieFactory(Duration.ofSeconds(seconds), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

}