package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import io.jsonwebtoken.io.DecodingException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.time.Clock;

/**
 * 외부 JWT 설정을 사용해 JwtTokenProvider를 등록한다.
 */

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {


    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        String secretKey = properties.secretKey();

        // 키 설정이 없으면 서버 시작 단계에서 실패한다.
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("JWT 서명 키 설정은 필수입니다.");
        }

        SecretKey signingKey;

        try {
            // 외부 설정의 Base64 문자열을 실제 서명 키 바이트로 복원한다.
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);

            //HS256에 필요한 최소 키 길이인 32바이트 이상인지 검증한다.
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (DecodingException | WeakKeyException exception) {
            throw new IllegalStateException(
                    "everyone.jwt.secret-key는 Base64로 인코딩되어야 하며, "
                            + "디코딩한 키 길이는 32바이트 이상이어야 합니다.",
                    exception
            );
        }

        if (properties.refreshTokenValidity() == null) {
            throw new IllegalArgumentException("everyone.jwt.refresh-token-validity 설정은 필수입니다.");
        }

        return new JwtTokenProvider(
                signingKey,
                properties.accessTokenValidity(),
                // 외부 설정에서 읽은 Refresh Token 유효기간을 전달한다.
                properties.refreshTokenValidity(),
                // 운영에서는 실제 현재 시각을 사용한다.
                Clock.systemUTC()
        );
    }
}
