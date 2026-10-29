package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * everyone.sse 설정을 Duration으로 바인딩한다. 기본값과 환경변수 연결은 application.yaml에 있다.
 *
 * @param heartbeatInterval 하트비트 작업 완료 후 다음 실행까지의 간격.
 *                          스케줄러의 Scheduled 설정도 같은 프로퍼티 키를 사용한다
 * @param connectionTimeout 개별 비동기 HTTP 연결의 제한 시간. 하트비트 간격과 별도로 적용한다
 */
@ConfigurationProperties( prefix = "everyone.sse")
public record SseProperties(
        Duration heartbeatInterval,
        Duration connectionTimeout
) {
}
