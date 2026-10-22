package com.codeit.sb13.playlist_for_everyone.global.exception.user;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class UserNotFoundException extends UserException {

    public UserNotFoundException( Map<String, Object> details) {
        super(ApiErrorCode.USER_NOT_FOUND, details);
    }

    public UserNotFoundException( Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.USER_NOT_FOUND, details, cause);
    }
}
