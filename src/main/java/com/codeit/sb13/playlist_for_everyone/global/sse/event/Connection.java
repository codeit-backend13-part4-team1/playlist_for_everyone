package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import java.util.UUID;

/**
 * 한 번의 SSE 연결을 식별하는 값이다. 사용자 ID나 전송 이벤트 ID와 구분한다.
 * 같은 사용자가 여러 탭을 열거나 재연결하면 매번 새 값을 사용하여 이전 연결만 정리할 수 있게 한다.
 *
 * @param id 서버에서 생성한 연결 UUID
 */
public record Connection(UUID id) {
    /** 새로운 HTTP 스트림에 사용할 연결 식별자를 발급한다. */
    public static Connection create(){
        return new Connection(UUID.randomUUID());
    }
}
