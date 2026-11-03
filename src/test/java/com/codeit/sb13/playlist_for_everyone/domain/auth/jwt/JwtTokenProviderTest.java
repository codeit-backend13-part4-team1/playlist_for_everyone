package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT Access Token 발급 및 검증")
class JwtTokenProviderTest {


    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    // JWT 발급, 검증 테스트에만 사용하는 공개 테스트 키다.
    private static final String TEST_SECRET_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    // 실행 날짜에 영향을 받지 않도록 현재 시각을 고정한다.
    private static final Instant NOW =
            Instant.parse("2026-10-26T00:00:00Z");

    // 테스트용 유효 시간이다. 운영 유효 시간을 설정에서 관리한다.
    private static final Duration ACCESS_TOKEN_VALIDITY =
            Duration.ofMinutes(15);

    private static final Duration REFRESH_TOKEN_VALIDITY =
            Duration.ofDays(7);

    private SecretKey signingKey;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // 설정에 사용하는 Base64 문자열을 실제 서명 키로 변환한다.
        signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(TEST_SECRET_KEY)
        );

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        // Spring 컨텍스트나 DB 없이 실제 Provider를 테스트한다
        jwtTokenProvider = new JwtTokenProvider(
                signingKey,
                ACCESS_TOKEN_VALIDITY,
                REFRESH_TOKEN_VALIDITY,
                clock
        );
    }

    @Test
    @DisplayName("Access Token에 사용자 ID, jti, 종류와 발급, 만료 시각을 담는다")
    void Access_Token에_필요한_정보를_담는다() {
        String token = jwtTokenProvider.createAccessToken(USER_ID);

        //Provider의 파싱 메서드와 독립적으로 발급 결과를 확인한다.
        Claims claims = readClaims(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());

        //jti는 개별 토큰을 구분하는 고유 식별자다.
        assertThat(claims.getId()).isNotBlank();

        assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");

        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);

        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(ACCESS_TOKEN_VALIDITY));

    }

    @Test
    @DisplayName("같은 사용자자에게 같은 시각에 발급해도 jti가 서로 다르다")
    void 토큰을_발급할_때바다_서로_다른_jti를_사용한다() {
        //Clock이 고정되어 있으므로 두 토큰의 사용자 ID와 발급 시각은 같다.
        String firstToken = jwtTokenProvider.createAccessToken(USER_ID);
        String secondToken = jwtTokenProvider.createAccessToken(USER_ID);

        Claims firstClaims = readClaims(firstToken);
        Claims secondClaims = readClaims(secondToken);


        assertThat(firstClaims.getId()).isNotBlank();
        assertThat(secondClaims.getId()).isNotBlank();

        // 발급할 때마다 새로운 식별자를 생성하는지 확인한다.
        assertThat(firstClaims.getId()).isNotEqualTo(secondClaims.getId());
    }


    @Test
    @DisplayName("유효한 Access Token을 검ㅈ으하면 사용자 ID를 반한다")
    void 유효한_Access_Token에서_사용자_ID를_반환한다() {
        String token = jwtTokenProvider.createAccessToken(USER_ID);

        UUID result = jwtTokenProvider.parseAccessToken(token);

        assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("만료된 Access Token은 거절한다.")
    void 만료된_Access_Token은_거절한다() {
        // 현재 시각보다 1초 전에 만려된 토큰을 준비한다.
        String token = createSignedToken(
                USER_ID.toString(),
                "ACCESS",
                NOW.minusSeconds(1),
                signingKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거절한다")
    void 다른_키로_서명한_토큰은_거절한다() {
        SecretKey anotherKey = Jwts.SIG.HS256.key().build();

        // 내용이 정상이더라도 서버의 키로 서명하지 않았으면 거절해야 한다.
        String token = createSignedToken(
                USER_ID.toString(),
                "ACCESS",
                NOW.plus(ACCESS_TOKEN_VALIDITY),
                anotherKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("JWT 형식이 아닌 문자열은 거절한다")
    void JWT_형식이_아니면_거절한다() {
        assertThatThrownBy(
                () -> jwtTokenProvider.parseAccessToken("not-a-jwt")
        ).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Refresh Token을 Access Token으로 사용할 수 없다")
    void Refresh_Token은_Access_Token으로_인정하지_않는다() {
        // 서명과 만료 시각이 정상이더라도 종류가 다르면 거절해야 한다.
        String token = createSignedToken(
                USER_ID.toString(),
                "REFRESH",
                NOW.plus(ACCESS_TOKEN_VALIDITY),
                signingKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("사용자 ID가 없는 토큰은 거절한다")
    void 사용자_ID가_없으면_거절한다() {
        String token = createSignedToken(
                null,
                "ACCESS",
                NOW.plus(ACCESS_TOKEN_VALIDITY),
                signingKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("사용자 ID가 UUID 형식이 아니면 거절한다")
    void 사용자_ID가_UUID가_아니면_거절한다() {
        String token = createSignedToken(
                "invalid-user-id",
                "ACCESS",
                NOW.plus(ACCESS_TOKEN_VALIDITY),
                signingKey
        );

        // UUID 변환 실패도 JWT 검증 실패로 처리하도록 계약을 정한다.
        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료 시각이 없는 토큰은 거절한다")
    void 만료_시각이_없으면_거절한다() {
        String token = createSignedToken(
                USER_ID.toString(),
                "ACCESS",
                null,
                signingKey
        );

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    /**
     * 발급된 토큰을 JJWT로 직접 검증하고 내용을 읽는다.
     *
     * 테스트에서도 고정 시각을 사용해야 실제 실행 날짜에 따라
     * 토큰이 만료된 것으로 판단되는 문제를 피할 수 있다.
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
     * 검증 테스트에 필요한 토큰을 직접 만든다.
     * <p>
     * Provider의 발급 메서드를 사용하지 않으므로
     * 만료되거나 필수 정보가 빠진 토큰도 준비할 수 있다.
     */
    private String createSignedToken(
            String subject,
            String tokenType,
            Instant expiresAt,
            SecretKey key
    ) {
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                // 만료 테스트에서 발급 시각이 만료 시각보다 앞서도록 한다.
                .issuedAt(Date.from(NOW.minusSeconds(60)))
                .claim("tokenType", tokenType);
        if (subject != null) {
            builder.subject(subject);
        }
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }
        return builder
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

}