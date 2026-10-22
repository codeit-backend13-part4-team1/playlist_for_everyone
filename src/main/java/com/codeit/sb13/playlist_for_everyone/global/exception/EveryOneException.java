package com.codeit.sb13.playlist_for_everyone.global.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public abstract class EveryOneException extends RuntimeException {

    private final ApiErrorCode apiErrorCode;
    private final Map<String, Object> details;

    protected EveryOneException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode.getMessage());
        this.apiErrorCode = apiErrorCode;
        this.details = getDetailsOrDefault(details);
    }

    protected EveryOneException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode.getMessage(), cause);
        this.apiErrorCode = apiErrorCode;
        this.details = getDetailsOrDefault(details);
    }

    private static Map<String, Object> getDetailsOrDefault(Map<String, Object> details) {
        return details == null ? Map.of() : details;
    }

}
