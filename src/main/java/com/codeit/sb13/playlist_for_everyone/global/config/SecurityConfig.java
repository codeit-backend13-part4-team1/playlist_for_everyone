package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtAuthenticationFilter;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// 개발 초기 임시 보안 설정: dev 프로필에서만 적용
// JWT 인증·인가와 쿠키 기반 CSRF 정책으로 전환한다.
// 운영 보안 정책 적용을 완료한 뒤 @Profile("dev") 제한을 제거한다.
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   UserService userService){

        // 인증 정보가 없거나 유효하지 않으면 401을 반환한다
        // JWT 필터 내부의 인증 실패와 보호 API의 미인증 접근에 함께 사용한다.
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, authException)
                        -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

        // Servlet 필터로 자동 등록되지 않도록 여기에서 직접 생성한다.
        // 이 필터는 아래에서 Spring Security 체인에만 등록한다.
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, userService, authenticationEntryPoint);

        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler((request, response, denied) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // 사용자 목록 조회는 관리자만 접근할 수 있다. hasRole("ADMIN")은 ROLE_ADMIN 권한을 검사한다.
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // 접근 권한을 판단하기 전에 JWT 인증 저보를 설정한다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();

    }
}
