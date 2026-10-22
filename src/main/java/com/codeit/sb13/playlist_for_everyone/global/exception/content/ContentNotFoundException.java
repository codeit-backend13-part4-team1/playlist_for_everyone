package com.codeit.sb13.playlist_for_everyone.global.exception.content;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class ContentNotFoundException extends ContentException {

    public ContentNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.CONTENT_NOT_FOUND, details);
    }

    public ContentNotFoundException( Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.CONTENT_NOT_FOUND, details, cause);
    }
}
