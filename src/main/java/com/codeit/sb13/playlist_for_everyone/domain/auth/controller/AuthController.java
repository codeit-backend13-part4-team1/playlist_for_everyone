package com.codeit.sb13.playlist_for_everyone.domain.auth.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {


    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final boolean refreshCookieSecure;

    public AuthController(AuthService authService, JwtProperties jwtProperties,
                          @Value("${everyone.jwt.refresh-cookie-secure:true}") boolean refreshCookieSecure){
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.refreshCookieSecure = refreshCookieSecure;
    }


    /**
     * Refresh Token 쿠키로 새 Access Token과 Refresh Token을 발급한다.
     *
     * Access Token은 JSON으로 반환하고
     * Refresh Token은 기존 쿠키를 교체하여 전달한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtDto> reissue(@CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken) {
        // 쿠키 누락과 빈 값은 서비스 호출 전에 인증 실패로 처리한다.
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("유효하지 않은 인증 정보입니다.");
        }

        // JWT 검증, 사용자 확인, Redis의 원자적 교체르 수행한다.
        // 실패하면 예외가 발생하므로 새 쿠키와 성공 응답을 작성하지 않는다.
        TokenReissueResult result = authService.reissue(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie
                .from("REFRESH_TOKEN", result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(jwtProperties.refreshTokenValidity())
                .build();

        // 내부 결과 전채가 아닌 공식 응답 형식인 JwtDto만 변환한다.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.jwtDto());

    }


    /**
     *  로그인 전에 사용할 CSRF 토큰을 쿠키로 전달한다.
     *
     *  토큰 생성과 쿠키 저장은 Spring Security가 담당하며,
     *  응답 본문 없이 204 상태를 반환한다.
     */
    @GetMapping("/csrf-token")
    public ResponseEntity<Void> issueCsrfToken(CsrfToken csrfToken) {
        csrfToken.getToken();

        return ResponseEntity.noContent().build();
    }

}
