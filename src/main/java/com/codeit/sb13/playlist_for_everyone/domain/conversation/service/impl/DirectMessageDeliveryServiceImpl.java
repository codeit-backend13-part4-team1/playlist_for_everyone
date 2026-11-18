package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 현재 서버의 Simple Broker로 DM을 전달한다. 다른 서버로의 전달은 Redis 구독 흐름이 담당한다.
 *
 * <p>구독자가 없어도 DB에 저장된 메시지는 유지되며, 다음 접속에서 REST 조회로 확인할 수 있다.
 * MessagingException은 메시지·대화방 ID만 기록하고 여기서 재시도하지 않는다.
 * 이 단계는 원래 송신 요청과 별도 수신 흐름이므로 전달 실패를 송신자 응답으로 되돌리지 않는다.
 */
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
