package com.codeit.sb13.playlist_for_everyone.global.exception.sse;

public class SsePublishException extends RuntimeException {
    public SsePublishException(Throwable cause) {
        super("SSE 이벤트 발행에 실패했습니다.", cause);
    }
}
