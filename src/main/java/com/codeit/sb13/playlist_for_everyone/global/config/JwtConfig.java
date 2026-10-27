package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부 JWT 설정을 사용해 JwtTokenProvider를 등록한다.
 */

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {


    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        // RED 단계 : 설정 연결은 아직 구현하지 않는다.
        throw new UnsupportedOperationException("JWT 설정 연결 구현 전");
    }
}
