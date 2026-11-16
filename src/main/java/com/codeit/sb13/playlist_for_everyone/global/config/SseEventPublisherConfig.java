package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.HistorySavingSseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

/**
 * 일반 호출부에는 이력 저장 발행기를 주입하고, 내부 delegate에는 전송 구현체만 연결한다.
 * @Primary는 일반 SseEventPublisher 주입을, Qualifier는 자기 자신을 감싸지 않도록 전송 후보를 구분한다.
 * Kafka 전환 시 전송 구현체를 교체할 수 있지만 Redis 이력 저장소까지 자동 교체되지는 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class SseEventPublisherConfig {

    @Bean
    @Primary
    public SseEventPublisher sseEventPublisher(
            SseHistoryStore historyStore,
            @Qualifier("sseTransportPublisher")
            SseEventPublisher delegate,
            ObjectMapper objectMapper
    ){
        return new HistorySavingSseEventPublisher(historyStore, delegate, objectMapper);
    }
}
