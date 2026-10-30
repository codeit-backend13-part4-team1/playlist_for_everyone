package com.codeit.sb13.playlist_for_everyone.global.exception.playlist;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import java.util.Map;

public class PlayListSubscribeOwnException extends PlayListException {

  public PlayListSubscribeOwnException(Map<String, Object> details) {
    super(ApiErrorCode.PLAYLIST_SUBSCRIBE_OWN,details );
  }

  public PlayListSubscribeOwnException(Map<String, Object> details, Throwable cause) {
    super(ApiErrorCode.PLAYLIST_SUBSCRIBE_OWN,details, cause);
  }

}
