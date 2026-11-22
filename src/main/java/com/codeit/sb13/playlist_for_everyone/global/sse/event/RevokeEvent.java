package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import java.util.Objects;
import java.util.UUID;

public record RevokeEvent<T>(UUID userId, UUID loginSessionId) {
    public RevokeEvent {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(loginSessionId, "로그인 세션 ID는 필수입니다.");
    }
}
