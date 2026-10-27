package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

        // Base64 문자열을 원래의 키 바이트로 복원한다.
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);


        // 키가 너무 짧으면 WeakKeyException이 발생한다.
        SecretKey signingKey = Keys.hmacShaKeyFor(keyBytes);

        return new JwtTokenProvider(
                signingKey,
                properties.accessTokenValidity(),
                // 운영에서는 실제 현재 시각을 사용한다.
                Clock.systemUTC()
        );
    }
}
