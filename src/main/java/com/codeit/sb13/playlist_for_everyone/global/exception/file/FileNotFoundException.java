package com.codeit.sb13.playlist_for_everyone.global.exception.file;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FileNotFoundException extends FileException {

  public FileNotFoundException(Map<String, Object> details) {
    super(ApiErrorCode.FILE_NOT_FOUND, details);
  }

  public FileNotFoundException(Map<String, Object> details, Throwable cause) {
    super(ApiErrorCode.FILE_NOT_FOUND, details, cause);
  }

}
