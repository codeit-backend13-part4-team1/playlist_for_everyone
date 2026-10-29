package com.codeit.sb13.playlist_for_everyone.global.exception.playlist;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import java.util.Map;

public class PlayListForbiddenException extends PlayListException {

  public PlayListForbiddenException(Map<String, Object> details) {
    super(ApiErrorCode.PLAYLIST_FORBIDDEN, details);
  }

  public PlayListForbiddenException(Map<String, Object> details, Throwable cause) {
    super(ApiErrorCode.PLAYLIST_FORBIDDEN, details, cause);
  }
}
