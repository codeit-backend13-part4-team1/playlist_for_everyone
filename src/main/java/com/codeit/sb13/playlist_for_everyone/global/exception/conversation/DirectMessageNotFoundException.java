package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class DirectMessageNotFoundException extends ConversationException {
    public DirectMessageNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.DIRECT_MESSAGE_NOT_FOUND, details);
    }
}
