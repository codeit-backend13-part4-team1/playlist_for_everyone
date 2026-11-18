package com.codeit.sb13.playlist_for_everyone.global.exception.notification;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class NotificationInvalidCursorException extends NotificationException {
    public NotificationInvalidCursorException(Throwable cause) {
        super(
                ApiErrorCode.INVALID_REQUEST,
                Map.of("field", "cursor"),
                cause
        );
    }

}
