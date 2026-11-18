package com.codeit.sb13.playlist_for_everyone.global.exception.user;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class DuplicateEmailException extends UserException {

    public DuplicateEmailException(String email) {
        super(ApiErrorCode.USER_EMAIL_DUPLICATED, Map.of("email", email));
    }

    public DuplicateEmailException(String email, Throwable cause) {
        super(ApiErrorCode.USER_EMAIL_DUPLICATED, Map.of("email", email), cause);
    }

}
