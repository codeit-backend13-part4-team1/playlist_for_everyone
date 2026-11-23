package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.LoginSessionIssuer;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.RefreshTokenCookieFactory;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.SocialAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인 성공, 실패 후 프론트 화면으로 리다이렉트한다.
 */
@Component
@RequiredArgsConstructor
public class SocialLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    static final String SUCCESS_URL = "/#/contents";
    static final String FAILURE_URL ="/#/sign-in?error=oauth_failed&error_message=";

    private final SocialIdentityExtractor  socialIdentityExtractor;
    private final SocialAccountService socialAccountService;
    private final LoginSessionIssuer loginSessionIssuer;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }
}
