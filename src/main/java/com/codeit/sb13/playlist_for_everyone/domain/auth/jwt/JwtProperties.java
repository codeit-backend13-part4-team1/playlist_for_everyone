package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 발급에 필요한 외부 설정
 *
 * secret-key: Base64로 인코딩한 서명 키
 * access-token-validity: Access Token 유효 시간
 */

@ConfigurationProperties(prefix = "everyone.jwt")
public record JwtProperties(
        String secretKey,
        Duration accessTokenValidity
) {
}
