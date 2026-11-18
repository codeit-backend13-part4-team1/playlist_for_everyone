package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public class WebSocketSessionRegisterException extends EveryOneException {
    public WebSocketSessionRegisterException() {
        super(ApiErrorCode.INTERNAL_SERVER_ERROR,Map.of());
    }

    public WebSocketSessionRegisterException(Throwable cause) {
        super(ApiErrorCode.INTERNAL_SERVER_ERROR, Map.of(),cause);
    }

}
