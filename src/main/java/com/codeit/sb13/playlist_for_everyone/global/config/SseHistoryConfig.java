package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/** 저장·스냅샷·후속 조회 Lua를 서로 다른 이름의 빈으로 등록한다. 각 호출 안에서 이력과 매핑을 함께 처리한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SseHistoryProperties.class)
public class SseHistoryConfig {

    @Bean
    public RedisScript<List> sseHistoryAppendScript() {
        return RedisScript.of(new ClassPathResource("scripts/sse/sse_history_append.lua"), List.class);
    }
    @Bean
    public RedisScript<List> sseHistorySnapshotScript() {
        return RedisScript.of(new ClassPathResource("scripts/sse/sse_history_snapshot.lua"), List.class);
    }
    @Bean
    public RedisScript<List> sseHistoryReadAfterScript() {
        return RedisScript.of(new ClassPathResource("scripts/sse/sse_history_read_after.lua"), List.class);
    }
}
