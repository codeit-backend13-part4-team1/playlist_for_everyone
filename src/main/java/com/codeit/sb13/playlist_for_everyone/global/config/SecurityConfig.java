package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtAuthenticationFilter;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtAuthenticationFailureHandler;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtAuthenticationSuccessHandler;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.JwtLogoutSuccessHandler;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   UserService userService,
                                                   AuthenticationManager authenticationManager,
                                                   ObjectMapper objectMapper,
                                                   Environment environment,
                                                   RefreshTokenRepository refreshTokenRepository,
                                                   JwtProperties jwtProperties,
                                                   AuthService authService,
                                                   UserSessionRepository userSessionRepository,
                                                   ApplicationEventPublisher eventPublisher,
                                                   @Value("${everyone.jwt.refresh-cookie-secure:true}")
                                                   boolean refreshCookieSecure) throws Exception{

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
                new JwtAuthenticationFilter(jwtTokenProvider, userService, userSessionRepository, authenticationEntryPoint);

        // JWT 발급, Redis 저장 TTL, 쿠키 보관 기간에 같은 유효기간을 사용한다.
        // Secure 여부는 실행 환경의 설정으로 결정한다.
        JwtAuthenticationSuccessHandler successHandler =
                new JwtAuthenticationSuccessHandler(jwtTokenProvider, userService, objectMapper,
                        refreshTokenRepository,userSessionRepository, eventPublisher,
                        jwtProperties.refreshTokenValidity(), refreshCookieSecure
                );

        // Refresh Token 삭제와 쿠키 만료, 로그아웃 응답을 담당한다.
        // 쿠키 삭제 시에도 로그인 때와 동일한 Secure 설정을 사용한다.
        JwtLogoutSuccessHandler logoutSuccessHandler =
                new JwtLogoutSuccessHandler(authService, objectMapper, refreshCookieSecure);


        JwtAuthenticationFailureHandler failureHandler =
                new JwtAuthenticationFailureHandler(objectMapper);
        // 개발 환경에서만 Swagger 문서를 인증 없이 허용한다.
        // prod가 함께 활성화된 겨우에는 공개하지 않는다.
        boolean allowSwagger =
                environment.acceptsProfiles(Profiles.of("dev & !prod"));

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");

        csrfTokenRepository.setCookiePath("/");

        return http
                .authenticationManager(authenticationManager)
                // CSRF 보호를 활성화하고 쿠키 기반 저장소를 사용한다.
                // POST, PUT, PATCH, DELETE 등의 요청은 CSRF 검증 대상이 된다.
                .csrf(csrf -> csrf
                        // 프론트가 쿠키에서 읽은 원본 토큰을 헤더로 보내는 방식을 지원한다.
                        .spa()
                        // SPA 기본 설정 후, 쿠키 이름, 헤더, 경로 설정을 적용한다.
                        .csrfTokenRepository(csrfTokenRepository)
                        // SockJS 폴백 전송은 POST지만 인증은 STOMP 헤더 토큰으로 하므로 CSRF 대상이 아니다.
                        .ignoringRequestMatchers("/ws/**")
                )
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutRequestMatcher(
                                PathPatternRequestMatcher.withDefaults()
                                        .matcher(HttpMethod.POST, "/api/auth/sign-out")
                        )
                        .logoutSuccessHandler(logoutSuccessHandler)
                )
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
                            // 로그인 요청을 보내기 전에도 CSRF 토큰을 받을 수 있어야 한다.
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/auth/csrf-token"
                            ).permitAll()

                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/users",
                                    "/api/auth/sign-in",
                                    "/api/auth/refresh"
                            ).permitAll()

                            // 사용자 목록 조회는 관리자 권한이 필요하다.
                            .requestMatchers(HttpMethod.GET, "/api/users")
                            .hasRole("ADMIN")

                            // 사용자 권한과 계정 잠금 상태는 관리자만 변경할 수 있다.
                            .requestMatchers(HttpMethod.PATCH,
                                    "/api/users/{userId}/role",
                                    "/api/users/{userId}/locked"
                            ).hasRole("ADMIN")

                            // 콘텐츠 생성, 수정, 삭제는 관리자 권한이 필요하다.
                            .requestMatchers(HttpMethod.POST, "/api/contents")
                            .hasRole("ADMIN")

                            .requestMatchers(HttpMethod.PATCH, "/api/contents/*")
                            .hasRole("ADMIN")

                            .requestMatchers(HttpMethod.DELETE, "/api/contents/*")
                            .hasRole("ADMIN")

                            // 로그인 전 프론트 페이지를 표시하기 위한 경로다.
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/",
                                    "/index.html",
                                    "/assets/**",
                                    "/favicon.svg",
                                    "/placeholder-movie.png"
                            ).permitAll()
                            .requestMatchers("/ws", "/ws/**")
                            .permitAll()

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
