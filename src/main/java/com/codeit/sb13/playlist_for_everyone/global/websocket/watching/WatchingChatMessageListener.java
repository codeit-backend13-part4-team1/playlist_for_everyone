package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 관련 redis 메세지를 처리하는 리스너.
 * destination 을 받아서 해당 컨텐츠 주소로 퍼블리싱 한다. publishing.
 */

@Slf4j
@Component
public class WatchingChatMessageListener extends RedisMessageListener {

    static private final String pathPrefix = "/sub/contents/";

    public WatchingChatMessageListener(RedisTemplate<String, Object> redis) {
        super(redis);
    }

    @Override
    public void run(Object object){


    }

    // content subscribe 주소 반환.
    private String destination(String contentId){
        return pathPrefix + contentId + "/chat";
    }
}
