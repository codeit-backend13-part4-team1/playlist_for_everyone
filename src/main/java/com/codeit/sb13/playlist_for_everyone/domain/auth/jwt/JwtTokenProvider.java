package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import io.jsonwebtoken.*;

import javax.crypto.SecretKey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Jwt Access Token과 Refresh Token의 발급 검증을 담당한다.
 *
 * 사용자 조회, 토큰 저장, 로그인 처리와 HTTP 응답 작성은
 * 다른 클래스에서 담당한다.
 */
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey signingKey;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;
    private final Clock clock;
    private final JwtParser jwtParser;


    /**
     * Access Token과 Refresh Token의 유효시간은 각각 전달받는다.
     */

    public JwtTokenProvider(SecretKey signingKey, Duration accessTokenValidity, Duration refreshTokenValidity, Clock clock) {
        this.signingKey = Objects.requireNonNull(signingKey, "서명 키는 필수입니다.");
        this.accessTokenValidity = Objects.requireNonNull(accessTokenValidity, "Access Token 유효 시간은 필수입니다.");
        this.refreshTokenValidity = Objects.requireNonNull(refreshTokenValidity, "Refresh Token 유효 시간은 필수입니다.");
        this.clock = Objects.requireNonNull(clock, "시간은 필수입니다.");

        if (accessTokenValidity.isZero() || accessTokenValidity.isNegative()) {
            throw new IllegalArgumentException("Access Token 유효시간은 0보다 커야 합니다.");
        }

        if (refreshTokenValidity.isZero() || refreshTokenValidity.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 유효시간은 0보다 커야 합니다.");
        }

        // 발급 검증에서 동일한 Clock을 사용한다.
        // 테스트에서는 고정 시각, 서버에서는 현재 시각을 사용한다.
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(this.clock.instant()))
                .build();
    }

    /**
     * 사용자 ID와 고유 토큰 ID를 담은 Access Token을 발급한다.
     */
    public String createAccessToken(UUID userId) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다");

        Instant now = clock.instant();
        Instant expiration = now.plus(accessTokenValidity);

        return Jwts.builder()
                // sub: 이 토큰의 대상 사용자.
                .subject(userId.toString())
                // jti: 개별 토큰 식별자. 발급할 때마다 새로 생성한다
                .id(UUID.randomUUID().toString())
                // Refresh Token과 구분하기 위한 토큰 종류
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                // HS256 알고리즘과 서버의 비밀 키로 서명한다.
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 서명, 만료 시각, 토큰 종류와 사용자 ID를 검증한다.
     *
     * 검증에 성공하면 사용자 ID를 반환하고,
     * 실패하면 JwtException 계열 예외를 발생시킨다.
     */
    public UUID parseAccessToken(String token) {

        if (token == null || token.isBlank()) {
            throw new JwtException("토큰이 비어 있습니다");
        }

        // 서명된 JWT만 허용한다.
        // 서명 불일치, 잘못된 형식, 만료 등의 오류는 JJWT가 예외로 반환한다.
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();

        // 서명이 유효하더라도 Refresh Token이면 사용할 수 없다.
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new JwtException("Access Token이 아닙니다");
        }

        // JJWT의 만료 검사는 exp가 있을 때 적용되므로,
        // 우리 서비스에서는 exp 자체도 필수로 검증한다.
        if (claims.getExpiration() == null) {
            throw new JwtException("토큰에 만료 시각이 없습니다.");
        }

        String subject = claims.getSubject();

        if (subject == null ||  subject.isBlank()) {
            throw new JwtException("토큰에 사용자 ID가 없습니다");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new JwtException(
                    "토큰의 사용자 ID 형식이 올바르지 않습니다.", exception
            );
        }
    }

    /**
     * 사용자 ID와 고유 jti를 포함하는 Refresh Token을 발급한다.
     *
     * Redis 저장과 쿠키 발급은 호출하는 쪽에서 처리한다.
     */
    public String createRefreshToken(UUID userId) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다");

        Instant now = clock.instant();
        Instant expiration = now.plus(refreshTokenValidity);

        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh Token의 서명, 만료, 종류와 필수 정보를 검증한다.
     *
     * 성공하면 사용자 ID를 반환한다.
     * Redis에 저장된 토큰인지, 이미 폐기되었는지는 서비스에서 검증한다.
     */

    public UUID parseRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("토큰이 비어 있습니다.");
        }

        // 서명 불일치, 만료, 잘못된 JWT 형식은 JJWT가 예외로 처리한다.
        // ExpiredJwtException도 그대로 호출부에 전달한다.
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();

        // 같은 키로 서명된 Access Token도 재발급 용도로 사용할 수 없다.
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!REFRESH_TOKEN_TYPE.equals(tokenType)) {
            throw new JwtException("Refresh Token이 아닙니다.");
        }

        // JJWT는 exp가 있을 때 만료를 검사하므로, 존재 여부도 확인 한다.
        if (claims.getExpiration() == null) {
            throw new JwtException("토큰에 만료 시각이 없습니다.");
        }

        // 개별 Refresh Token을 식별할 수 있어야 한다.
        String jti = claims.getId();

        if (jti == null || jti.isBlank()) {
            throw new JwtException("토큰에 식별자가 없습니다.");
        }

        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new JwtException("토큰에 사용자 ID가 없습니다.");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new JwtException("토큰의 사용자 ID 형식이 올바르지 않습니다.", exception);
        }
    }
}
