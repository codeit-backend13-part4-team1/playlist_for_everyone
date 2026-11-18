package com.codeit.sb13.playlist_for_everyone.global.exception.follow;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FollowCreationFailedException extends FollowException {
    public FollowCreationFailedException(Throwable cause) {
        super(ApiErrorCode.FOLLOW_CREATION_FAILED, Map.of(), cause);
    }
}
