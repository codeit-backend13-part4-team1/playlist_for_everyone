package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public abstract class WebSocketException extends EveryOneException {
    protected WebSocketException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    protected WebSocketException(ApiErrorCode apiErrorCode, Map<String, Object> details,Throwable cause) {
        super(apiErrorCode, details,cause);
    }

}
