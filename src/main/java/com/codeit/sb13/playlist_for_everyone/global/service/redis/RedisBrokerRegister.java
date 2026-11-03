package com.codeit.sb13.playlist_for_everyone.global.service.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 공용 Redis 컨테이너에 리스너와 채널을 등록·해제한다. 컨테이너 자체의 시작·종료는 Spring이 담당한다. */
@Component
@RequiredArgsConstructor
public class RedisBrokerRegister {

    private final RedisMessageListenerContainer redisMessageListenerContainer;

    public void register(MessageListener listener, String topic){
        check(listener, topic);
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(topic));
    }

    public void unregister(MessageListener listener,String topic){
        check(listener);
        redisMessageListenerContainer.removeMessageListener(listener, new ChannelTopic(topic));
    }

    // 전달받은 인자 중 null이 있으면 NullPointerException을 던져 등록·해제를 중단한다.
    private void check(Object ...os) {
        for (Object o : os) {
            Objects.requireNonNull(o);
        }
    }

}
