package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class DestinationInvalidException extends WebSocketException {
    public DestinationInvalidException(Throwable cause) {
        super(ApiErrorCode.DESTINATION_INVALID, Map.of(), cause);
    }

}
