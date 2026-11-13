package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;

public record DirectMessageCommand (
        Conversation conversation,
        User sender,
        User receiver,
        String content){
}
