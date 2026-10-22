package com.codeit.sb13.playlist_for_everyone.global.exception.follow;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FollowNotFoundException extends FollowException {

    public FollowNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.FOLLOW_NOT_FOUND, details);
    }

    public FollowNotFoundException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.FOLLOW_NOT_FOUND, details, cause);
    }
}
