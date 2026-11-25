package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.LoginSessionIssuer;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.RefreshTokenCookieFactory;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.SocialAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * 소셜 로그인 성공, 실패 후 프론트 화면으로 리다이렉트한다.
 *
 * <p>성공하면 Refresh Token 쿠키만 발급하고 콘텐츠 화면으로 이동한다.
 * 토큰은 주소에 넣지 않으며, 프론트가 refresh API로 Access Token을 받는다.
 * 실패하면 사유 코드만 쿼리로 전달하고, 이메일 등 사용자 정보는 주소와 로그에 남기지 않는다.
 * <p>이동 주소는 설정된 서비스 주소(everyone.oauth2.redirect-base-url) 기준의 절대 주소로 만든다.
 * 상대 경로를 넘기면 Tomcat이 요청 Host 기준으로 바꾸는데, CloudFront를 거치면 Host가 ALB 주소가 된다.
 */
@Slf4j
@Component
public class SocialLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    static final String SUCCESS_PATH = "/#/contents";
    static final String FAILURE_PATH = "/#/sign-in?error=oauth_failed&error_message=";

    private final SocialIdentityExtractor socialIdentityExtractor;
    private final SocialAccountService socialAccountService;
    private final LoginSessionIssuer loginSessionIssuer;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final String redirectBaseUrl;

    public SocialLoginHandler(SocialIdentityExtractor socialIdentityExtractor,
                              SocialAccountService socialAccountService,
                              LoginSessionIssuer loginSessionIssuer,
                              RefreshTokenCookieFactory refreshTokenCookieFactory,
                              @Value("${everyone.oauth2.redirect-base-url}") String redirectBaseUrl) {
        this.socialIdentityExtractor = socialIdentityExtractor;
        this.socialAccountService = socialAccountService;
        this.loginSessionIssuer = loginSessionIssuer;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.redirectBaseUrl = StringUtils.trimTrailingCharacter(redirectBaseUrl, '/');
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        LoginSessionIssuer.LoginTokens tokens;
        try {
            SocialIdentity identity = socialIdentityExtractor.extract(authentication);
            UUID userId = socialAccountService.resolveUserId(identity);
            tokens = loginSessionIssuer.issue(userId);
        } catch (AuthenticationException exception) {
            // 이미 가입된 이메일, 미인증 이메일, 잠김 계정 등 사유가 정해진 거부
            onAuthenticationFailure(request, response, exception);
            return;
        } catch (RuntimeException exception) {
            // DB·Redis 오류 등은 사유를 노출하지 않고 login_failed로 통일한다.
            onAuthenticationFailure(request, response,
                    new SocialLoginException(SocialLoginError.LOGIN_FAILED, exception));
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookieFactory.create(tokens.refreshToken()).toString());
        response.sendRedirect(redirectBaseUrl + SUCCESS_PATH);
    }

    /**
     * 실패 사유를 코드로 바꿔 로그인 화면으로 보낸다.
     * 사용자가 동의 화면에서 취소한 경우는 정상 흐름이라 로그를 남기지 않는다.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        SocialLoginError error = resolveError(exception);
        logFailure(error, exception);

        SecurityContextHolder.clearContext();
        response.sendRedirect(redirectBaseUrl + FAILURE_PATH + error.code());
    }

    private SocialLoginError resolveError(AuthenticationException exception) {
        if (exception instanceof SocialLoginException socialLoginException) {
            return socialLoginException.getError();
        }
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth2Exception.getError().getErrorCode())) {
            return SocialLoginError.ACCESS_DENIED;
        }
        return SocialLoginError.LOGIN_FAILED;
    }

    // 예외 메시지에는 이메일 등이 들어갈 수 있어 사유 코드와 예외 타입으로 기록한다.
    private void logFailure(SocialLoginError error, AuthenticationException exception) {
        if (error == SocialLoginError.ACCESS_DENIED) {
            return;
        }

        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
        if (error == SocialLoginError.LOGIN_FAILED) {
            log.warn("소셜 로그인 실패. error={}, exceptionType={}", error.code(), cause.getClass().getSimpleName());
            return;
        }
        log.info("소셜 로그인 거부. error={}", error.code());
    }
}
