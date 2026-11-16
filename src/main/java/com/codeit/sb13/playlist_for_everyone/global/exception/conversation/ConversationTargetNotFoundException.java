package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class ConversationTargetNotFoundException extends ConversationException {
    public ConversationTargetNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.CONVERSATION_TARGET_NOT_FOUND, details);
    }
}
