package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
import org.springframework.stereotype.Service;

@Service
public class DirectMessageErrorSenderImpl implements DirectMessageErrorSender {

    @Override
    public void sendToSession(String sessionId, Throwable exception) {
    }
}
