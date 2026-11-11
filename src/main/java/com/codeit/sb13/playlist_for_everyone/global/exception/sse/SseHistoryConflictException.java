package com.codeit.sb13.playlist_for_everyone.global.exception.sse;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class SseHistoryConflictException extends SseException {
   public SseHistoryConflictException(UUID receiverId, UUID eventId){
       super(
               ApiErrorCode.SSE_HISTORY_CONFLICT,
               Map.of(
                       "receiverId", receiverId,
                       "eventId", eventId)
               );
   }
}
