package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class ConversationCreationFailedException extends ConversationException {
    public ConversationCreationFailedException(Throwable cause) {
        super(ApiErrorCode.CONVERSATION_CREATION_FAILED, Map.of(), cause);
    }
}
