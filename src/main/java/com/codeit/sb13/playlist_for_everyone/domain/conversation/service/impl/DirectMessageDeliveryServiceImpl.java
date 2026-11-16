package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectMessageDeliveryServiceImpl implements DirectMessageDeliveryService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendToLocalSubscribers(MessageDto message) {

        String destination = "/sub/conversations/" + message.conversationId() + "/direct-messages";

        try {
            messagingTemplate.convertAndSend(destination, message);
        } catch (MessagingException e){
            log.warn("DM WebSocket 전송에 실패했습니다. directMessageId={},conversationId={},exceptionType={}",
                    message.id(), message.conversationId(), e.getClass().getSimpleName());
        }

    }
}
