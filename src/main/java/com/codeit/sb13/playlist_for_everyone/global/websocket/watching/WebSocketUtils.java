package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketIllegalArgvException;

import java.util.Objects;

public class WebSocketUtils {
    static public <T> T check(T obj){
        try {
            return Objects.requireNonNull(obj);
        } catch (NullPointerException e) {
            throw new WebSocketIllegalArgvException(e);
        }
    }
}
