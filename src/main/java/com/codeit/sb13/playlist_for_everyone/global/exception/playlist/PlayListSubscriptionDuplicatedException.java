package com.codeit.sb13.playlist_for_everyone.global.exception.playlist;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import java.util.Map;

public class PlayListSubscriptionDuplicatedException extends PlayListException {

  public PlayListSubscriptionDuplicatedException(Map<String, Object> details) {
    super(ApiErrorCode.PLAYLIST_SUBSCRIPTION_DUPLICATED, details);
  }

  public PlayListSubscriptionDuplicatedException(Map<String, Object> details, Throwable cause) {
    super(ApiErrorCode.PLAYLIST_SUBSCRIPTION_DUPLICATED, details, cause);
  }
}