package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class SelfConversationNotAllowedException extends ConversationException {
    public SelfConversationNotAllowedException() {
        super(ApiErrorCode.SELF_CONVERSATION_NOT_ALLOWED, Map.of());
    }
}
