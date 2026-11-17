package com.codeit.sb13.playlist_for_everyone.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    // 콘텐츠 검색 캐시 이름
    public static final String CONTENT_SEARCH = "contentSearch";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisSerializer")
            GenericJacksonJsonRedisSerializer serializer
    ) {
        // 공통 Redis 캐시 설정
        // Key는 문자열, Value는 JSON으로 직렬화하며 null 값은 캐싱하지 않음
        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(new StringRedisSerializer())
                        )
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(serializer)
                        )
                        .disableCachingNullValues();

        // 콘텐츠 검색 캐시 TTL 5분 설정
        RedisCacheConfiguration contentSearchConfig =
                defaultConfig.entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues();

        // 공통 설정을 적용하고 콘텐츠 검색 캐시를 별도로 등록
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(
                        Map.of(CONTENT_SEARCH, contentSearchConfig)
                )
                .build();
    }

    // 서버 시작 시 캐시 매니저와 등록된 캐시 이름 출력
    @Bean
    public ApplicationRunner cacheStartupReport(CacheManager cacheManager) {
        return args -> {
            log.info("[캐시] 매니저 = {}",
                    cacheManager.getClass().getSimpleName());

            cacheManager.getCacheNames().forEach(name ->
                    log.info("[캐시] 등록된 캐시 = {}", name));
        };
    }

}
