package com.codeit.sb13.playlist_for_everyone.global.exception.file;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FileInvalidTypeException extends FileException {

    public FileInvalidTypeException(Map<String, Object> details) {
        super(ApiErrorCode.FILE_INVALID_TYPE, details);
    }

    public FileInvalidTypeException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.FILE_INVALID_TYPE, details, cause);
    }
}
