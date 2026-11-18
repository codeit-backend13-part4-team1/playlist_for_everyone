package com.codeit.sb13.playlist_for_everyone.global.exception.review;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class ReviewAlreadyExistsException extends ReviewException {

    public ReviewAlreadyExistsException(Map<String, Object> details) {
        super(ApiErrorCode.REVIEW_ALREADY_EXISTS, details);
    }

    public ReviewAlreadyExistsException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.REVIEW_ALREADY_EXISTS, details, cause);
    }
}
