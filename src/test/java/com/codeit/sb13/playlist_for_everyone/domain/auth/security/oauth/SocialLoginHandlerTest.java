package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.LoginSessionIssuer;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.RefreshTokenCookieFactory;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.SocialAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("소셜 로그인 성공·실패 처리")
class SocialLoginHandlerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final SocialIdentity IDENTITY =
            new SocialIdentity(SocialProvider.GOOGLE, "google-sub", "test@gmail.com", "테스트", true);

    @Mock
    private SocialIdentityExtractor socialIdentityExtractor;

    @Mock
    private SocialAccountService socialAccountService;

    @Mock
    private LoginSessionIssuer loginSessionIssuer;

    private SocialLoginHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final Authentication authentication = new TestingAuthenticationToken("oauth-user", null);

    @BeforeEach
    void setUp() {
        // 쿠키 속성은 실제 팩터리로 만들어 운영과 같은 Set-Cookie를 검증한다.
        handler = new SocialLoginHandler(socialIdentityExtractor, socialAccountService,
                loginSessionIssuer, new RefreshTokenCookieFactory(Duration.ofDays(7), true));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그인에 성공하면 Refresh 쿠키를 발급하고 콘텐츠 화면으로 이동한다.")
    void 로그인에_성공하면_Refresh_쿠키를_발급하고_콘텐츠_화면으로_이동한다() throws Exception {
        given(socialIdentityExtractor.extract(authentication)).willReturn(IDENTITY);
        given(socialAccountService.resolveUserId(IDENTITY)).willReturn(USER_ID);
        given(loginSessionIssuer.issue(USER_ID))
                .willReturn(new LoginSessionIssuer.LoginTokens("access-token", "refresh-token"));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/#/contents");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("REFRESH_TOKEN=refresh-token;")
                .contains("HttpOnly");
        // 토큰은 주소에 남기지 않고, 프론트가 refresh API로 Access Token을 받는다.
        assertThat(response.getRedirectedUrl())
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = SocialLoginError.class,
            names = {"EMAIL_ALREADY_EXISTS", "EMAIL_NOT_VERIFIED", "ACCOUNT_LOCKED"})
    @DisplayName("정책상 거부되면 사유 코드와 함께 로그인 화면으로 이동하고 세션을 시작하지 않는다.")
    void 정책상_거부되면_사유_코드와_함께_로그인_화면으로_이동하고_세션을_시작하지_않는다(SocialLoginError error)
            throws Exception {
        given(socialIdentityExtractor.extract(authentication)).willReturn(IDENTITY);
        given(socialAccountService.resolveUserId(IDENTITY)).willThrow(new SocialLoginException(error));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/sign-in?error=oauth_failed&error_message=" + error.code());
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        verify(loginSessionIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("로그인 세션 시작에 실패하면 쿠키 없이 로그인 실패 화면으로 이동한다.")
    void 로그인_세션_시작에_실패하면_쿠키_없이_로그인_실패_화면으로_이동한다() throws Exception {
        given(socialIdentityExtractor.extract(authentication)).willReturn(IDENTITY);
        given(socialAccountService.resolveUserId(IDENTITY)).willReturn(USER_ID);
        given(loginSessionIssuer.issue(USER_ID)).willThrow(new IllegalStateException("Redis 저장 실패"));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/sign-in?error=oauth_failed&error_message=login_failed");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("사용자가 동의 화면에서 취소하면 access_denied로 이동한다.")
    void 사용자가_동의_화면에서_취소하면_access_denied로_이동한다() throws Exception {
        handler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/sign-in?error=oauth_failed&error_message=access_denied");
    }

    @Test
    @DisplayName("그 외 OAuth 오류는 login_failed로 이동한다.")
    void 그_외_OAuth_오류는_login_failed로_이동한다() throws Exception {
        handler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token_response")));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/#/sign-in?error=oauth_failed&error_message=login_failed");
    }

    @Test
    @DisplayName("실패하면 남아 있는 인증 정보를 지운다.")
    void 실패하면_남아_있는_인증_정보를_지운다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication);

        handler.onAuthenticationFailure(request, response,
                new SocialLoginException(SocialLoginError.LOGIN_FAILED));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}