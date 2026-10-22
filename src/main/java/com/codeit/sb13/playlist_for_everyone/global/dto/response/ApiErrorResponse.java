package com.codeit.sb13.playlist_for_everyone.global.dto.response;

import java.util.Map;

public record ApiErrorResponse(
    String exceptionName,
    String message,
    Map<String, Object> details
) {
}
