package com.codeit.sb13.playlist_for_everyone.global.exception.file;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FileDeleteException extends FileException {

    public FileDeleteException(Map<String, Object> details) {
        super(ApiErrorCode.FILE_DELETE_FAILED, details);
    }

    public FileDeleteException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.FILE_DELETE_FAILED, details, cause);
    }
}
