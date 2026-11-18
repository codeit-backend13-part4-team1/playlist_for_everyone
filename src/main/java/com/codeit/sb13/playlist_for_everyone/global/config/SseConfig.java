package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 설정 record를 Spring 빈으로 등록하여 연결 서비스에서 주입받도록 한다.
 * 환경별 값을 ConfigurationProperties로 바인딩할 때 SseProperties 생성자의 검증도 수행한다.
 * 허용 범위를 벗어난 설정은 빈 생성에 실패하여 애플리케이션 시작 단계에서 확인할 수 있다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SseProperties.class)
public class SseConfig {
}
