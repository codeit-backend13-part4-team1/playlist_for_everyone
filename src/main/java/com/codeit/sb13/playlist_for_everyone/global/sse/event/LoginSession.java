package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import java.util.Objects;
import java.util.UUID;

/**
 * JWT 인증에서 검증한 로그인 세션 ID를 SSE 연결 등록에 전달한다.
 * 같은 로그인 세션에 속한 여러 연결이 이 값을 공유할 수 있다.
 *
 * @param id 로그인 세션 ID({@code sid})
 */
public record LoginSession(UUID id) {
    public LoginSession {
        Objects.requireNonNull(id);
    }
}
