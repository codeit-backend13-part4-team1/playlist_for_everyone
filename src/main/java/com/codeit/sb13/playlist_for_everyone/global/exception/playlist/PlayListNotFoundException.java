package com.codeit.sb13.playlist_for_everyone.global.exception.playlist;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class PlayListNotFoundException extends PlayListException {

    public PlayListNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.PLAYLIST_NOT_FOUND, details);
    }

    public PlayListNotFoundException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.PLAYLIST_NOT_FOUND, details, cause);
    }
}
