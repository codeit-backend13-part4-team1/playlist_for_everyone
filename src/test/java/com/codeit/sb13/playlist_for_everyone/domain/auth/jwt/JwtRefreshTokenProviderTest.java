package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.util.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT Refresh Token 발급 및 검증")
class JwtRefreshTokenProviderTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // 공개 가능한 테스트 전용키다. 실제 서버 키와는 별개다.
    private static final String TEST_SECRET_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    private static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofMinutes(15);

    // 테스트에 사용하는 값이며, 실제 운영 정책을 확정하는 값은 아니다.
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);

    private SecretKey signingKey;
    private JwtTokenProvider jwtTokenProvider;


    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET_KEY));

        // Spring 컨텍스트나 Redis 없이 실제 Provider만 검증한다.
        jwtTokenProvider = new JwtTokenProvider(
                signingKey,
                ACCESS_TOKEN_VALIDITY,
                REFRESH_TOKEN_VALIDITY,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Refresh Token에 사용자 ID, jti, 종류와 발급·만료 시각을 담는다")
    void Refresh_Token에_필요한_정보를_담는다() {

        String token = jwtTokenProvider.createRefreshToken(USER_ID);

        // Provider의 검증 메서드와 독립적으로 발급 결과를 확인한다.
        Claims claims = readClaims(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("tokenType", String.class)).isEqualTo("REFRESH");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(REFRESH_TOKEN_VALIDITY));
    }

    @Test
    @DisplayName("같은 사용자에게 같은 시각에 발급해도 Refresh Token의 jti는 다르다")
    void Refresh_Token을_발급할_때마다_다른_jti를_사용한다() {
        String firstToken = jwtTokenProvider.createRefreshToken(USER_ID);
        String secondToken = jwtTokenProvider.createRefreshToken(USER_ID);

        Claims firstClaims = readClaims(firstToken);
        Claims secondClaims = readClaims(secondToken);

        assertThat(firstClaims.getId()).isNotBlank();
        assertThat(secondClaims.getId()).isNotBlank();
        assertThat(firstClaims.getId()).isNotEqualTo(secondClaims.getId());
        assertThat(firstToken).isNotEqualTo(secondToken);
    }

    @Test
    @DisplayName("유효한 Refresh Token에서 사용자 ID를 반환한다.")
    void 유효한_Refresh_Token에서_사용자_ID를_반환한다() {
        // 발급 가능과 독립적으로 검증 기능을 테스트 한다.
        String token = createSingedToken(
                USER_ID.toString(),
                "REFRESH",
                NOW.plus(REFRESH_TOKEN_VALIDITY),
                UUID.randomUUID().toString(),
                signingKey
        );

        UUID result = jwtTokenProvider.parseRefreshToken(token);

        assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("만료된 Refresh Token은 거절한다")
    void 만료된_Refresh_Token은_거절한다() {
        String token = createSingedToken(
                USER_ID.toString(),
                "REFRESH",
                NOW.minusSeconds(1),
                UUID.randomUUID().toString(),
                signingKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseRefreshToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명한 Refresh Token은 거절한다")
    void 다른_키로_서명한_Refresh_Token은_거절한다() {
        SecretKey anotherKey = Jwts.SIG.HS256.key().build();

        String token = createSingedToken(
                USER_ID.toString(),
                "REFRESH",
                NOW.plus(REFRESH_TOKEN_VALIDITY),
                UUID.randomUUID().toString(),
                anotherKey
        );

        assertThatThrownBy(()-> jwtTokenProvider.parseRefreshToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("JWT 형식이 아닌 문자열은 Refresh Token으로 인정하지 않는다")
    void JWT_형식이_아니면_거절한다() {
        assertThatThrownBy(()-> jwtTokenProvider.parseRefreshToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Access Token을 Refresh Token으로 사용할 수 없다.")
    void Access_Token을_Refresh_Token으로_인정하지_않는다() {
        String accessToken = jwtTokenProvider.createAccessToken(USER_ID);

        assertThatThrownBy(() -> jwtTokenProvider.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("실제로 발급한 Refresh Token을 Access Token으로 사용할 수 없다.")
    void 발급한_Refresh_Token_Access_Token으로_인정하지_않는다() {
        // 기존 테스트의 직접 만든 토큰에 더해 실제 발급 결과도 확인 한다.
        String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID);

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "missing-subject",
            "invalid-subject",
            "missing-expiration",
            "missing-jti"
    })
    @DisplayName("Refresh Token의 필수 정보가 없거나 잘못되면 거절한다.")
    void 필수_정보가_올바르지_거절한다(String scenario) {
        String subject = USER_ID.toString();
        Instant expiresAt = NOW.plus(REFRESH_TOKEN_VALIDITY);
        String jti = UUID.randomUUID().toString();

        // 서명 자체는 정상인 토큰을 만들어 내용 검증을 확인한다.
        switch (scenario) {
            case "missing-subject" -> subject = null;
            case "invalid-subject" -> subject = "invalid-user-id";
            case "missing-expiration" -> expiresAt = null;
            case "missing-jti" -> jti = null;
            default -> throw new IllegalArgumentException("알 수 없는 테스트 시나리오: " + scenario);
        }

        String token = createSingedToken(
                subject,
                "REFRESH",
                expiresAt,
                jti,
                signingKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseRefreshToken(token))
                .isInstanceOf(JwtException.class);
    }

    /**
     * 발급 결과를 JJWT로 직접 읽는다.
     * 검증 시각도 고정해 실제 실행 날짜의 영향을 제거한다.
     */
    private Claims readClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 검증 테스트용 토큰을 직접 만든다.
     * 필수 정보가 누락되거나 만료되 토큰도 준비할 수 있다.
     */

    private String createSingedToken(String subject, String tokenType, Instant expiresAt, String jti, SecretKey key) {
        JwtBuilder builder = Jwts.builder()
                .issuedAt(Date.from(NOW.minusSeconds(60)))
                .claim("tokenType", tokenType);

        if (subject != null) {
            builder.subject(subject);
        }

        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }

        if (jti != null) {
            builder.id(jti);
        }

        return builder
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }


}
