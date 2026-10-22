package com.codeit.sb13.playlist_for_everyone.global.exception.review;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class ReviewNotFoundException extends ReviewException {

    public ReviewNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.REVIEW_NOT_FOUND, details);
    }

    public ReviewNotFoundException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.REVIEW_NOT_FOUND, details, cause);
    }
}
