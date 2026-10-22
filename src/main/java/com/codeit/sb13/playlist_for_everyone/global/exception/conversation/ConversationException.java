package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public abstract class ConversationException extends EveryOneException {

    protected ConversationException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    protected ConversationException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode, details, cause);
    }
}
