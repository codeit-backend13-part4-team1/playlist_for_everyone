package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Objects;

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

    // npe 방지를 위해 o 가 null 이면 IllegalArgumentException 반환.
    private void check(Object ...os) {
        for (Object o : os) {
            Objects.requireNonNull(o);
        }
    }

}
