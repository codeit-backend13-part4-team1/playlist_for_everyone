package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class WebSocketAuthInvalidException extends WebSocketException {
    public WebSocketAuthInvalidException() {
        super(ApiErrorCode.WEBSOCKET_AUTHORIZATION_INVALID, Map.of());
    }
    public WebSocketAuthInvalidException(Throwable cause) {
        super(ApiErrorCode.WEBSOCKET_AUTHORIZATION_INVALID, Map.of(), cause);
    }
}
