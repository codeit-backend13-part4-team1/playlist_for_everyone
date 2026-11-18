package com.codeit.sb13.playlist_for_everyone.global.exception.websocket;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public class WebSocketIllegalArgvException extends EveryOneException {
    public WebSocketIllegalArgvException(Throwable cause) {
        super(ApiErrorCode.INTERNAL_SERVER_ERROR, Map.of(), cause);
    }
}
