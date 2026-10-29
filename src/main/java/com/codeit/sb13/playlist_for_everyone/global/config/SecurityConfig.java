package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtAuthenticationFilter;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtAuthenticationFailureHandler;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtAuthenticationSuccessHandler;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity

public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   UserService userService,
                                                   AuthenticationManager authenticationManager,
                                                   ObjectMapper objectMapper,
                                                   Environment environment) throws Exception{

        // 인증 정보가 없거나 JWT 검증에 실패한 경우의 응답이다.
        // 로그인 시 이메일·비밀번호 검증 실패는 기존 로그인 실패 핸들러가 담당한다.
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception) -> {
                    ApiErrorResponse errorResponse = new ApiErrorResponse(
                            "UnauthorizedException",
                            "인증이 필요합니다.",
                            Map.of()
                    );
                    // sendError 대신 응답을 직접 작성해 공통 json 형식을 유지한다.
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                    objectMapper.writeValue(response.getWriter(), errorResponse);
                };
        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) -> {
                    ApiErrorResponse errorResponse = new ApiErrorResponse(
                            "AccessDeniedException",
                            "접근 권한이 없습니다.",
                            Map.of()
                    );

                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                    objectMapper.writeValue(response.getWriter(), errorResponse);
                };

        // Servlet 필터로 자동 등록되지 않도록 여기에서 직접 생성한다.
        // 이 필터는 아래에서 Spring Security 체인에만 등록한다.
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenProvider, userService, authenticationEntryPoint);

        JwtAuthenticationSuccessHandler successHandler =
                new JwtAuthenticationSuccessHandler(jwtTokenProvider, userService, objectMapper);

        JwtAuthenticationFailureHandler failureHandler =
                new JwtAuthenticationFailureHandler(objectMapper);
        // 개발 환경에서만 Swagger 문서를 인증 없이 허용한다.
        // prod가 함께 활성화된 겨우에는 공개하지 않는다.
        boolean allowSwagger =
                environment.acceptsProfiles(Profiles.of("dev & !prod"));

        return http
                .authenticationManager(authenticationManager)
                // CSRF 정책은 추후 별도로 진행 합니다.
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                // 토큰 무효화를 포함한 로그아웃은 이후 단계에서 구현한다.
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    if (allowSwagger) {
                        // 문서 조회 경로만 허용한다.
                        // Swagger에서 실제 API를 호출할 때는 기존 JWT 인증이 필요하다.
                        auth.requestMatchers(
                                HttpMethod.GET,
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll();
                    }
                    auth
                            // 회원가입과 로그인은 인증 없이 접근할 수 있다.
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/users",
                                    "/api/auth/sign-in"
                            ).permitAll()

                            // 사용자 목록 조회는 관리자 권한이 필요하다.
                            .requestMatchers(HttpMethod.GET, "/api/users")
                            .hasRole("ADMIN")

                            // 로그인 전 프론트 페이지를 표시하기 위한 경로다.
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/",
                                    "/index.html",
                                    "/assets/**",
                                    "/favicon.svg"
                            ).permitAll()

                            .anyRequest().authenticated();
                })
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
