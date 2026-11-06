package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

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
