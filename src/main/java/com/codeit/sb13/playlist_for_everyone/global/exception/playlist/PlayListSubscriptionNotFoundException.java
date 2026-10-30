package com.codeit.sb13.playlist_for_everyone.global.exception.playlist;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import java.util.Map;

public class PlayListSubscriptionNotFoundException extends PlayListException {

  public PlayListSubscriptionNotFoundException(Map<String, Object> details) {
    super(ApiErrorCode.PLAYLIST_SUBSCRIPTION_NOT_FOUND, details);
  }

  public PlayListSubscriptionNotFoundException(Map<String, Object> details, Throwable cause) {
    super(ApiErrorCode.PLAYLIST_SUBSCRIPTION_NOT_FOUND, details, cause);
  }
}