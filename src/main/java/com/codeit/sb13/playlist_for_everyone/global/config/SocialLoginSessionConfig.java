package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

import java.util.List;

/**
 * 소셜 로그인 요청(state)을 보관하는 Redis 세션 설정.
 *
 * <p>롤링 배포나 서버 여러 대 환경에서 로그인을 시작한 서버와 콜백을 받은 서버가 달라도 동작하도록
 * 세션을 Redis에 저장한다. 비인덱스 저장소를 사용해 ElastiCache에서 막힌 CONFIG 명령을 쓰지 않는다.
 * 세션 필터는 소셜 로그인 경로에만 등록하므로 다른 API 요청은 Redis 세션을 조회하지 않는다.
 */
@Configuration
@EnableRedisHttpSession(
        maxInactiveIntervalInSeconds = 600,
        redisNamespace = "everyone:oauth:session"
)
public class SocialLoginSessionConfig {

    private static final String SESSION_COOKIE_NAME = "OAUTH_SESSION";

    /**
     * 같은 필터를 등록 빈으로 감싸면 Spring Boot가 전체 경로에 따로 등록하지 않는다.
     */
    @Bean
    public FilterRegistrationBean<SessionRepositoryFilter<?>> socialLoginSessionFilterRegistration(
            SessionRepositoryFilter<?> springSessionRepositoryFilter) {
        FilterRegistrationBean<SessionRepositoryFilter<?>> registration =
                new FilterRegistrationBean<>(springSessionRepositoryFilter);
        registration.setUrlPatterns(List.of(SocialLoginSecurityConfig.SOCIAL_LOGIN_PATHS));
        // Spring Security 필터보다 먼저 실행되어야 보안 체인이 Redis 세션을 사용한다.
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
        return registration;
    }

    /**
     * 콜백은 Google·Kakao에서 돌아오는 최상위 GET 이동이라 SameSite=Lax 쿠키가 함께 전송된다.
     * Secure는 Refresh Token 쿠키와 같은 설정을 따른다.
     */
    @Bean
    public CookieSerializer cookieSerializer(@Value("${everyone.jwt.refresh-cookie-secure:true}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }
}
