package com.codeit.sb13.playlist_for_everyone.global.websocket.chat;

import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.RedisMessageChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageListener implements MessageListener {

    static private final String pathPrefix = "/sub/contents/";

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate template;


    // 메세지가 오면 메세지에 해당되는 주소로 메세지 전송.
    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        String dto = redis
                .getStringSerializer()
                .deserialize(message.getBody());

        RedisMessageChatDto chat = objectMapper.readValue(dto, RedisMessageChatDto.class);

        // 컨텐츠 id 기반 채팅 구독 주소로 메세지 전송.
        template.convertAndSend(
                destination(chat.content()),
                chat.contentChatDto()
        );
    }

    // content subscribe 주소 반환.
    private String destination(String contentId){
        return pathPrefix + contentId + "/chat";
    }
}
