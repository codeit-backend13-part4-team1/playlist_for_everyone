package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.global.service.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


/**
 * 채팅 관련 redis 메세지를 처리하는 리스너.
 * destination 을 받아서 해당 컨텐츠 주소로 퍼블리싱 한다. publishing.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingChatMessageListener implements MessageListener {

    static private final String pathPrefix = "/sub/contents/";

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate template;


    // 메세지가 오면 메세지에 해당되는 주소로 메세지 전송.
    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        String raw = redis
                .getStringSerializer()
                .deserialize(message.getBody());

        RedisMessageDto<?> container = objectMapper.readValue(raw, RedisMessageDto.class);

        String contentId = container.destination();
        ContentChatDto contentChatDto = (ContentChatDto) container.data();


        // 컨텐츠 id 기반 채팅 구독 주소로 메세지 전송.
        template.convertAndSend(
                destination(contentId),
                contentChatDto
        );
    }

    // content subscribe 주소 반환.
    private String destination(String contentId){
        return pathPrefix + contentId + "/chat";
    }
}
