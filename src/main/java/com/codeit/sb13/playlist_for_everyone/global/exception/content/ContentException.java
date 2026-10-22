package com.codeit.sb13.playlist_for_everyone.global.exception.content;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public abstract class ContentException extends EveryOneException {

    protected ContentException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    protected ContentException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode, details, cause);
    }
}
