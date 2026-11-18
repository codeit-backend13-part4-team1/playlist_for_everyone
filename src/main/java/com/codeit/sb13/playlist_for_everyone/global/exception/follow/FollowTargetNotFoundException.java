package com.codeit.sb13.playlist_for_everyone.global.exception.follow;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FollowTargetNotFoundException extends FollowException {
    public FollowTargetNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.FOLLOW_TARGET_NOT_FOUND, details);
    }
}
