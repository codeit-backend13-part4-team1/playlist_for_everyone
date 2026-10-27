package com.codeit.sb13.playlist_for_everyone.global.exception.follow;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class SelfFollowNotAllowedException extends FollowException {

    public SelfFollowNotAllowedException() {
        super(ApiErrorCode.SELF_FOLLOW_NOT_ALLOWED, Map.of());
    }
}
