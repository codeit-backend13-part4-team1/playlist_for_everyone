package com.codeit.sb13.testsupport.auth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Redis 대신 메모리 세션 저장소를 사용하는 테스트 설정.
 *
 * Redis 컨테이너가 없는 통합 테스트에서
 * @Import(InMemoryUserSessionConfig.class)로 사용한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryUserSessionConfig {

    /**
     * RedisUserSessionRepository 빈도 함께 등록되지만,
     * @Primary로 이 저장소가 우선 주입된다.
     */
    @Bean
    @Primary
    public UserSessionRepository inMemoryUserSessionRepository() {
        return new InMemoryUserSessionRepository();
    }
}
