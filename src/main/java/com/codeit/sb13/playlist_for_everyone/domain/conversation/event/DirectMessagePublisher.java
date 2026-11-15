package com.codeit.sb13.playlist_for_everyone.domain.conversation.event;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;

public interface DirectMessagePublisher {
    void publish(MessageDto message);
}
