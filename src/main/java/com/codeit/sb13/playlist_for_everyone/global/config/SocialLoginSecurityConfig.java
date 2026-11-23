package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth.SocialLoginHandler;
import jakarta.servlet.http.HttpServlet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

/**
 * 소셜 로그인 경로만 처리하는 보안 체인.
 *
 * <p>기존 JWT 체인(STATELESS)보다 먼저 검사하며, 소셜 로그인 경로가 아닌 요청은 기존 체인이 처리한다.
 * 로그인 요청(state)은 세션에 저장하고, 로그인 결과는 세션에 남기지 않는다.
 * 우리 서비스의 로그인 상태는 {@link SocialLoginHandler}가 발급하는 Refresh Token 쿠키로만 관리한다.
 */
@Configuration
public class SocialLoginSecurityConfig {

    // 로그인 시작 주소와 제공자 콜백 주소. Redis 세션 필터도 이 경로에만 적용한다.
    static final String[] SOCIAL_LOGIN_PATHS = {
            "/oauth2/authorization/*",
            "/login/oauth2/code/*"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain socialLoginSecurityFilterChain(HttpSecurity http,
                                                              SocialLoginHandler socialLoginHandler) throws Exception {
        return http
                .securityMatcher(SOCIAL_LOGIN_PATHS)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        // 기본 저장소는 제공자 Access Token을 서버 메모리에 계속 보관한다.
                        // 로그인 후에는 쓰지 않으므로 10분 뒤 만료되는 세션에만 둔다.
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .successHandler(socialLoginHandler)
                        .failureHandler(socialLoginHandler)
                )
                // Google·Kakao 인증 결과(SecurityContext)를 세션에 저장하지 않는다.
                .securityContext(context -> context
                        .securityContextRepository(new NullSecurityContextRepository())
                )
                .requestCache(cache -> cache.disable())
                .build();
    }

}
