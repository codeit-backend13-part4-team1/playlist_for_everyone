package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;

public interface DirectMessageDeliveryService {

    void sendToLocalSubscribers(MessageDto message);
}
