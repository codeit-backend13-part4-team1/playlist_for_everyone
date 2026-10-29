package com.codeit.sb13.playlist_for_everyone.global.websocket.chat;

import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.ContentChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.CHAT_CONTENT_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

    private final RedisTemplate<String, Object> template;

    public void publish(String contentId, ContentChatDto dto){
        template.convertAndSend(topic(contentId), dto);
    }

    // 토픽으로 메세지 전송
    private String topic(String contentId){
        // todo - content not found Exception 으로 대치
        if (contentId == null || contentId.isBlank())
            throw new RuntimeException();
        return CHAT_CONTENT_PREFIX + contentId;
    }

}
