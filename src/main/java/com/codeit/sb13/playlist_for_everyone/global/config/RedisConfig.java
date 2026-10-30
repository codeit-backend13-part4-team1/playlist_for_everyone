package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

@Configuration
public class RedisConfig {
    // redis Prefix
    public static final String WEBSOCKET_SESSION_PREFIX = "websocket:session:";
    public static final String WATCHER_CONTENT_PREFIX = "watcher:content:";
    public static final String CHAT_CONTENT_PREFIX = "chat:content:";
    // 클래스에서 자체적으로 템플릿을 만들어서 쓰면 삭제해도 무방할 듯.
    // 캐시 및 redis 데이터보관시 사용할 template
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory,
            @Qualifier("redisSerializer") GenericJacksonJsonRedisSerializer serializer
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 키는 문자열만 가능.
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 값은 모든 클래스 를 직렬화 가능.
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }


    // Redis 객체 직렬화 클래스
    @Bean("redisSerializer")
    public GenericJacksonJsonRedisSerializer redisSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()    // 모든 클래스를 대상으로 직렬화 가능.
                .build();
    }


    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory
    ){
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

}
