package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

public interface DirectMessageErrorSender {

    void sendToSession(String sessionId, Throwable exception);
}
