package com.codeit.sb13.playlist_for_everyone.global.exception.sse;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public abstract class SseException extends EveryOneException {
    protected SseException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    protected SseException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode, details, cause);
    }
}
