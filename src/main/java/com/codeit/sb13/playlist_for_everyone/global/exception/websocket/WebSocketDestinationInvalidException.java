package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class WebSocketDestinationInvalidException extends WebSocketException {
    public WebSocketDestinationInvalidException(Throwable cause) {
        super(ApiErrorCode.WEBSOCKET_DESTINATION_INVALID, Map.of(), cause);
    }
    public WebSocketDestinationInvalidException() {
        super(ApiErrorCode.WEBSOCKET_DESTINATION_INVALID, Map.of());
    }

}
