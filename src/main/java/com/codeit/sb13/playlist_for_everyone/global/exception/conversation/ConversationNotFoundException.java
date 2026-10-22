package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class ConversationNotFoundException extends ConversationException {

    public ConversationNotFoundException( Map<String, Object> details) {
        super(ApiErrorCode.CONVERSATION_NOT_FOUND, details);
    }

    public ConversationNotFoundException( Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.CONVERSATION_NOT_FOUND,details, cause);
    }
}
