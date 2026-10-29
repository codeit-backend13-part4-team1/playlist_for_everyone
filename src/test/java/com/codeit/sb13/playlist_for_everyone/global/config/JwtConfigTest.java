package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("JWT 설정 및 Bean 등록")
class JwtConfigTest {


    // 테스트 전용키다. 운영 설정에 복사해서 사용하지 않는다.
    // HS256에 적합한 키를 생성한 뒤 외부 설정처럼 Base64 문자열로 전달한다.
    private static final String TEST_SECRET_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    // 전체 서버 대신 JwtConfig에 필요한 작은 Spring 컨텍스트만 실행한다.
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(JwtConfig.class);


    @Test
    @DisplayName("설정한 서명 키와 유효 시간으로 동작하는 Provider를 등록한다.")
    void 정상_설정이면_Provider를_등록한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + TEST_SECRET_KEY,
                        "everyone.jwt.access-token-validity=30m",
                        "everyone.jwt.refresh-token-validity=7d"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtProperties.class);
                    assertThat(context).hasSingleBean(JwtTokenProvider.class);

                    JwtProperties properties =
                            context.getBean(JwtProperties.class);

                    // 문자열 설정이 Duration으로 변환되었는지 확인한다.
                    assertThat(properties.accessTokenValidity())
                            .isEqualTo(Duration.ofMinutes(30));


                    JwtTokenProvider provider =
                            context.getBean(JwtTokenProvider.class);

                    UUID userId = UUID.randomUUID();
                    String token = provider.createAccessToken(userId);

                    // 설정으로 전달한 키로 서명을 검증한다.
                    // Config가 임의의 다른 키를 사용하면 이 검증이 실패한다
                    Claims claims = Jwts.parser()
                            .verifyWith(Keys.hmacShaKeyFor(
                                    Decoders.BASE64.decode(TEST_SECRET_KEY)
                            ))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    assertThat(claims.getSubject())
                            .isEqualTo(userId.toString());

                    // 실제 발급된 토큰에도 설정한 유효 시간이 적용되어야 한다.
                    Duration actualValidity = Duration.between(
                            claims.getIssuedAt().toInstant(),
                            claims.getExpiration().toInstant()
                    );

                    assertThat(actualValidity)
                            .isEqualTo(Duration.ofMinutes(30));

                    // 등록된 Provider로 검증까지 수행할 수 있어야 한다.

                    assertThat(provider.parseAccessToken(token))
                            .isEqualTo(userId);
                });
    }


    @Test
    @DisplayName("서명 키 설정이 없으면 컨텍스트 시작에 실패한다")
    void 서명_키가_없으면_시작에_실패한다() {

        contextRunner
                .withPropertyValues(
                        "everyone.jwt.access-token-validity=30m",
                        "everyone.jwt.refresh-token-validity=7d"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    // Bean 생성 실패의 원인이 잘못된 설정인지 확인 한다.
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class
                            );
                });
    }

    @Test
    @DisplayName("서명 키가 너무 짧으면 컨텍스트 시작에 실패한다")
    void 짧은_서명_key면_시작에_실패한다() {
        // 16바이트 128비트이므로 HS256에 필요한 최소 256비트보다 짧다.
        String shortSecretKey =
                Encoders.BASE64.encode(new byte[16]);

        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + shortSecretKey,
                        "everyone.jwt.access-token-validity=30m",
                        "everyone.jwt.refresh-token-validity=7d"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    WeakKeyException.class
                            );
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(WeakKeyException.class)
                            .hasStackTraceContaining(
                                    "everyone.jwt.secret-key는 Base64로 인코딩되어야 하며, "
                                    + "디코딩한 키 길이는 32바이트 이상이어야 합니다.");
                });
    }

    @Test
    @DisplayName("Access Token 유효 시간이 0이면 컨텍스트 시작에 실패한다")
    void 유효_시간이_0이면_시작에_실패한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + TEST_SECRET_KEY,
                        "everyone.jwt.access-token-validity=0s",
                        "everyone.jwt.refresh-token-validity=7d"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    // Provider가 이미 가진 유효 시간 검증이 설정 연결 후에도
                    // 실제로 적용되는지 확인한다.
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class
                            );
                });
    }

    @Test
    @DisplayName("Base64 형식이 잘못되면 설정 이름을 포함한 오류를 시작에 실패한다.")
    void 잘못된_Base64_키면_오류를_알린다() {
        // 문자열 내부에 표준 Base64에서 허용하지 않는 '_'를 넣는다.
        String invalidSecretKey = "MDEy_MzQ1Njc4OWFiY2RlZg==";

        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + invalidSecretKey,
                        "everyone.jwt.access-token-validity=30m",
                        "everyone.jwt.refresh-token-validity=7d"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    // 실제 디코딩 오류는 원인 예외로 보존한다.
                    // 오류 체인에는 문제가 된 설정 이름과 필요한 조건이 있어야 한다.
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(DecodingException.class)
                            .hasStackTraceContaining(
                                    "everyone.jwt.secret-key는 Base64로 인코딩되어야 하며, "
                                            + "디코딩한 키 길이는 32바이트 이상이어야 합니다."
                            );
                });
    }


    @Test
    @DisplayName("설정한 Refresh Token 유효기간으로 발급·검증 가능한 Provider를 등록한다.")
    void Refresh_Token_유효기간을_Provider에_전달한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + TEST_SECRET_KEY,
                        "everyone.jwt.access-token-validity=30m",
                        // 기존 단위 테스트의 7일과 다른 값으로 설정을 전달한다.
                        "everyone.jwt.refresh-token-validity=3d"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtProperties.class);
                    assertThat(context).hasSingleBean(JwtTokenProvider.class);

                    JwtProperties properties = context.getBean(JwtProperties.class);


                    // 외부 설정의 "3d"가 Duration으로 바인딩 되어 있는지 확인 한다.
                    assertThat(properties.refreshTokenValidity())
                            .isEqualTo(Duration.ofDays(3));

                    JwtTokenProvider provider = context.getBean(JwtTokenProvider.class);

                    UUID userId = UUID.randomUUID();

                    // 직접 생성한 객체가 아니라 Spring이 등록한 Bean을 사용한다.
                    String token = provider.createRefreshToken(userId);

                    // 설정한 키로 실제 서명을 검증하고 토큰 내용을 읽는다.
                    Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(
                            Decoders.BASE64.decode(TEST_SECRET_KEY)))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    assertThat(claims.getSubject()).isEqualTo(userId.toString());
                    assertThat(claims.getId()).isNotBlank();
                    assertThat(claims.get("tokenType", String.class))
                            .isEqualTo("REFRESH");

                    // 현재 시각을 직접 비교하지 않고 발급~만료 사이의 기간을 검증한다.
                    Duration actualValidity = Duration.between(
                            claims.getIssuedAt().toInstant(),
                            claims.getExpiration().toInstant()
                    );

                    assertThat(actualValidity).isEqualTo(Duration.ofDays(3));
                    assertThat(provider.parseRefreshToken(token))
                            .isEqualTo(userId);
                });
    }


    @Test
    @DisplayName("Refresh Token 유효기간 설정이 없으면 컨텍스트 시작에 실패한다")
    void Refresh_Token_유효기간이_없으면_시작에_실패한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + TEST_SECRET_KEY,
                        "everyone.jwt.access-token-validity=30m"
                        // refresh-token-validity는 의도적으로 누락한다.
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    // 설정 누락을 시작 단계에서 명확한 오류로 알린다.
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("everyone.jwt.refresh-token-validity");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    @DisplayName("Refresh Token 유효기간이 0이면 시작에 실패한다")
    void Refresh_Token_유효기간이_0_이하면_시작에_실패한다(String validity) {

        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + TEST_SECRET_KEY,
                        "everyone.jwt.access-token-validity=30m",
                        "everyone.jwt.refresh-token-validity=" + validity
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    // Provider의 유효기간 검사가 설정 연결 후에도 적용되어야 한다.
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("Refresh Token 유효시간은 0보다 커야 합니다.");
                });
    }

}