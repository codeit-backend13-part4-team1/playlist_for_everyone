package com.codeit.sb13.playlist_for_everyone.global.exception.playlist;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import java.util.Map;

public class PlayListContentNotFoundException extends PlayListException {

  public PlayListContentNotFoundException(Map<String, Object> details) {
    super(ApiErrorCode.PLAYLIST_CONTENT_NOT_FOUND, details);
  }

  public PlayListContentNotFoundException(Map<String, Object> details, Throwable cause) {
    super(ApiErrorCode.PLAYLIST_CONTENT_NOT_FOUND, details, cause);
  }
}