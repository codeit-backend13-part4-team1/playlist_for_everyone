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
    // watchingSession 의 세션 저장소용 prefix
    public static final String STORAGE_SESSION_PREFIX = "storage:watching:session:";
    public static final String STORAGE_CONTENT_PREFIX = "storage:watching:content:";
    // watchingSession 의 데이터(Session change event, chat) 전달용 prefix
    public static final String MESSAGE_CHAT_PREFIX = "message:watching:chat:";
    public static final String MESSAGE_VIEW_PREFIX = "message:watching:view:";
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


    // SSE와 WebSocket 등이 공유한다. 각 기능은 자기 리스너만 등록·해제하고 컨테이너 생명주기는 Spring에 맡긴다.
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory
    ){
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

}
