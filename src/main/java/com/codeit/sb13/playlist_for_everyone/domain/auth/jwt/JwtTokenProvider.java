package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Jwt Access Token 발급과 검증을 담당한다.
 *
 * 사용자 조회, 로그인 처리, HTTP 응답 작성은 다른 클래스에서 담당한다.
 */
public class JwtTokenProvider {

    public JwtTokenProvider(SecretKey signingKey,
                            Duration accessTokenValidity,
                            Clock clock) {
        // GREEN 단계에서 전달받은 의존성을 필드에 보관한다.
    }

    /**
     * 사용자 ID와 고유 토큰 ID를 담은 Access Token을 발급한다.
     */
    public String createAccessToken(UUID userId) {
        throw new  UnsupportedOperationException("Access Token 발급 구현 전");
    }


    /**
     * 서명, 만료 시각, 토큰 종류와 필수 정보를 검증한다.
     * 검증에 성공하면 사용자 ID를 반환하고,
     * 실패하면 JwtException 계열 예외를 발생시킨다.
     */
    public UUID parseAccessToken(String token) {
        throw new UnsupportedOperationException("Access Token 검증 구현 전");
    }
}
