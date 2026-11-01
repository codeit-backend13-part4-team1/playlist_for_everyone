package com.codeit.sb13.playlist_for_everyone.global.exception.file;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FileUploadException extends FileException {

    public FileUploadException(Map<String, Object> details) {
        super(ApiErrorCode.FILE_UPLOAD_FAILED, details);
    }

    public FileUploadException(Map<String, Object> details, Throwable cause) {
        super(ApiErrorCode.FILE_UPLOAD_FAILED, details, cause);
    }
}
