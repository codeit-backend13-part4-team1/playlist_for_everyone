package com.codeit.sb13.playlist_for_everyone.global.exception.follow;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public abstract class FollowException extends EveryOneException {

    protected FollowException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    protected FollowException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode, details, cause);
    }
}
