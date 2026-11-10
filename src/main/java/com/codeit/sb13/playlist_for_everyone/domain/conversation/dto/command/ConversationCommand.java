package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;

public record ConversationCommand(User userA, User userB) {

    public ConversationCommand{
        String userAId = userA.getId().toString();
        String userBId = userB.getId().toString();
        if (userAId.compareTo(userBId) > 0) {
            User userACopy = userA;
            userA = userB;
            userB = userACopy;
        }
    }
}
