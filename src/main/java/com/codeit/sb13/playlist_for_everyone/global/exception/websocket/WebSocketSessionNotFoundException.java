package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public class WebSocketSessionNotFoundException extends EveryOneException {
    public WebSocketSessionNotFoundException() {
        super(ApiErrorCode.WEBSOCKET_SESSION_NOT_FOUND, Map.of());
    }
}
