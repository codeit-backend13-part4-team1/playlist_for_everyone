package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 설정 record를 Spring 빈으로 등록하여 연결 서비스에서 주입받도록 한다.
 * 환경별 설정값 해석은 Spring의 ConfigurationProperties 바인딩을 사용한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SseProperties.class)
public class SseConfig {
}
