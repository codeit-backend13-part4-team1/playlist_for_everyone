package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.UUID;

/**
 * 분산 인스턴스 환경에서 Redis 를 이용해서 모든 인스턴스가
 * 공유해야할 이벤트를 발행, 처리하는 클래스.
 * 이벤트에 대한 등록은 팩토리 클래스로 진행.
 */
@Slf4j
public abstract class RedisWebSocketEvent implements WebSocketEvent, MessageListener {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisSerializer<Object> serializer;

    // 이벤트 별로 고유한 토픽을 가지도록 UUID 를 설정.
    private final String topic = "websocket:event:" + UUID.randomUUID();

    public RedisWebSocketEvent(
            RedisTemplate<String, Object> redisTemplate,
            RedisSerializer<Object> serializer
    ) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
    }

    public String topic(){
        return topic;
    }

    /**
     * 이벤트에 사용항 데이터와 함께 redis 를 이용한 이벤트를 발행하는 매서드.
     * @param data
     */
    @Override
    public void publish(Object data) {
        redisTemplate.convertAndSend(topic, data);
    }

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        run(deserialize(message));
    }

    private Object deserialize(Message message){
        return serializer.deserialize(message.getBody());
    }

    /**
     * 이벤트 발행에 사용된 데이터 객체를 이용해서 이벤트에 해당하는 로직을 수행하는 매서드.
     * @param data 발행 할 이벤트 객체.
     */
    protected abstract void run(Object data);
}
