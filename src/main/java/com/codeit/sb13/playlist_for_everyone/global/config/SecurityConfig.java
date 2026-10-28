package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtAuthenticationFilter;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtAuthenticationFailureHandler;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtAuthenticationSuccessHandler;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity

public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   UserService userService,
                                                   AuthenticationManager authenticationManager,
                                                   ObjectMapper objectMapper) throws Exception{

        // 보호 API에 인증 없이 접근하거나 JWT 검증에 실패했을 때 사용한다.
        // 로그인 실패 핸들러와는 호출되는 상황이 다르다.
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception)
                        -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

        // Servlet 필터로 자동 등록되지 않도록 여기에서 직접 생성한다.
        // 이 필터는 아래에서 Spring Security 체인에만 등록한다.
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, userService, authenticationEntryPoint);

        JwtAuthenticationSuccessHandler successHandler =
                new JwtAuthenticationSuccessHandler(jwtTokenProvider, userService, objectMapper);

        JwtAuthenticationFailureHandler failureHandler =
                new JwtAuthenticationFailureHandler(objectMapper);

        return http
                .authenticationManager(authenticationManager)
                // CSRF 정책은 추후 별도로 진행 합니다.
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                // 토큰 무효화를 포함한 로그아웃은 이후 단계에서 구현한다.
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler((request, response, denied) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 회원가입 로그인 요청은 인증 없이 접근할 수 있다.
                        .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/sign-in").permitAll()
                        // 사용자 목록 조회는 관리자만 접근할 수 있다. hasRole("ADMIN")은 ROLE_ADMIN 권한을 검사한다.
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.svg").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginProcessingUrl("/api/auth/sign-in")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                // Bearer JWT 인증은 사용자명·비밀번호 로그인 필터보다 먼저 수행한다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();

    }
}
