package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseTransportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** SSE 전송 설정을 바인딩한다. Redis 연결 팩토리와 리스너 컨테이너는 기존 공용 설정을 사용한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SseTransportProperties.class)
public class SseTransportConfig {
}
