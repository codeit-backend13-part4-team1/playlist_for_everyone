package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.HistorySavingSseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

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
