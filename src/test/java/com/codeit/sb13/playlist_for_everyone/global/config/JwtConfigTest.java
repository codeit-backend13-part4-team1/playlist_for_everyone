package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                        "everyone.jwt.access-token-validity=30m"
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
                        "everyone.jwt.access-token-validity=30m"
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
                        "everyone.jwt.access-token-validity=30m"
                )
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    WeakKeyException.class
                            );
                });
    }

    @Test
    @DisplayName("Access Token 유효 시간이 0이면 컨텍스트 시작에 실패한다")
    void 유효_시간이_0이면_시작에_실패한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.jwt.secret-key=" + TEST_SECRET_KEY,
                        "everyone.jwt.access-token-validity=0s"
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

}