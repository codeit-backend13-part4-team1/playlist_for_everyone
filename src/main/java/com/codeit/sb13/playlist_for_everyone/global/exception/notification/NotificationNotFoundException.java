package com.codeit.sb13.playlist_for_everyone.global.exception.notification;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class NotificationNotFoundException extends NotificationException {

    public NotificationNotFoundException( Map<String, Object> details) {
        super(ApiErrorCode.NOTIFICATION_NOT_FOUND, details);
    }

    public NotificationNotFoundException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.NOTIFICATION_NOT_FOUND, details, cause);
    }
}
