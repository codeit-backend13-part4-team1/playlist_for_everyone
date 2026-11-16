package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectMessageDeliveryServiceImpl implements DirectMessageDeliveryService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendToLocalSubscribers(MessageDto message) {

    }
}
