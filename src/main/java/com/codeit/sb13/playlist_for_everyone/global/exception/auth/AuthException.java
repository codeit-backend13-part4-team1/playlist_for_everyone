package com.codeit.sb13.playlist_for_everyone.global.exception.auth;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;

import java.util.Map;

public abstract class AuthException extends EveryOneException {

    protected AuthException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }
}
