package com.codeit.sb13.playlist_for_everyone.global.exception.file;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FileEmptyException extends FileException {

    public FileEmptyException(Map<String, Object> details) {
        super(ApiErrorCode.FILE_EMPTY, details);
    }

    public FileEmptyException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.FILE_EMPTY, details, cause);
    }
}
