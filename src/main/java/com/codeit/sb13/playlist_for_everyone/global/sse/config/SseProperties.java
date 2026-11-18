package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * everyone.sse 설정을 Duration으로 바인딩하고, 생성 시 허용 범위를 검증한다.
 *
 * <p>기본값과 환경변수 연결은 application.yaml에 있다. 환경변수를 생략하면 YAML의
 * 기본값을 사용하지만, 최종 바인딩 값이 null이거나 허용 범위를 벗어나면 빈 생성에 실패한다.
 *
 * @param heartbeatInterval null이 아닌 양수. 하트비트 작업 완료 후 다음 실행까지의 간격이며,
 *                          스케줄러의 Scheduled 설정도 같은 프로퍼티 키를 사용한다
 * @param connectionTimeout null이 아닌 1ms 이상의 값. 개별 비동기 HTTP 연결의 제한 시간이며,
 *                          하트비트 간격과 별도로 적용한다
 */
@ConfigurationProperties( prefix = "everyone.sse")
public record SseProperties(
        Duration heartbeatInterval,
        Duration connectionTimeout
) {
    /**
     * 잘못된 설정을 애플리케이션 시작 시 거부한다. 직접 생성할 때도 같은 검증을 적용한다.
     *
     * <p>연결 제한 시간은 SseEmitter에 전달할 때 toMillis()로 변환한다.
     * 1ms 미만의 양수도 변환 결과가 0이 되므로 최소 1ms를 요구한다.
     *
     * @throws IllegalArgumentException 값이 null이거나 각 설정의 허용 범위를 벗어난 경우
     */
    public SseProperties {
        if (isInvalidDuration(heartbeatInterval)){
            throw new IllegalArgumentException("everyone.sse.heartbeat-interval은 0보다 커야 합니다.");
        }
        if (isInvalidDuration(connectionTimeout, 1)){
            throw new IllegalArgumentException("everyone.sse.connection-timeout은 1ms 이상이어야 합니다.");
        }
    }

    private boolean isInvalidDuration(Duration value){
        return value == null || value.isZero() || value.isNegative();
    }

    private boolean isInvalidDuration(Duration value, int millis){
        return value == null  || value.compareTo(Duration.ofMillis(millis)) < 0 ;
    }

}
