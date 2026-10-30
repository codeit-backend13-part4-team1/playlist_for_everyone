package com.codeit.sb13.playlist_for_everyone.global.exception.sse;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class SsePublishException extends SseException {
    public SsePublishException(Throwable cause) {
        super(ApiErrorCode.SSE_PUBLISH_FAIL, Map.of(), cause);
    }
}
