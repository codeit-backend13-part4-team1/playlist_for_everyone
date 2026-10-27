package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// 개발 초기 임시 보안 설정: dev 프로필에서만 적용
// JWT 인증·인가와 쿠키 기반 CSRF 정책으로 전환한다.
// 운영 보안 정책 적용을 완료한 뒤 @Profile("dev") 제한을 제거한다.
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http){

        return http
                // 개발 초기 API 연동을 위한 임시 전체 허용 정책
                // 기존 임시 설정.
                // 이번 GREEN 단게에서 JWT 필터와 접근 정책을 연결한다.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();

    }
}
