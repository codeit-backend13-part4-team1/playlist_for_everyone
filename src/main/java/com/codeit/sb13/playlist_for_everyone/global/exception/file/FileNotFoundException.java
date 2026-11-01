package com.codeit.sb13.playlist_for_everyone.global.exception.file;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

public class FileNotFoundException extends FileException {

  public FileNotFoundException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
    super(apiErrorCode, details);
  }

  public FileNotFoundException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
    super(apiErrorCode, details, cause);
  }

}
