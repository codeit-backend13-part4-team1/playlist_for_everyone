package com.codeit.sb13.playlist_for_everyone.domain.auth.event;

import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 세션이 무효화되었음을 알린다.
 *
 * SSE·웹소켓은 이 이벤트를 받아 revokedSessionId로 연결된 연결만 종료하도록 확장할 수 있다.
 * 사용자 ID만으로 연결을 종료하면 새 로그인의 연결까지 끊길 수 있으므로 세션 ID를 함께 전달한다.
 */
public record UserSessionRevokedEvent(
        UUID userId,
        UUID revokedSessionId,
        RevokeReason reason
) {

    public UserSessionRevokedEvent {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(revokedSessionId, "무효화된 세션 ID는 필수입니다.");
        Objects.requireNonNull(reason, "무효화 사유는 필수입니다.");
    }

    public enum RevokeReason {
        // 다른 곳에서 다시 로그인함
        DUPLICATE_LOGIN,
        // 관리자가 권한을 변경함,
        ROLE_CHANGED,
        // 관리자가 계정을 잠금
        LOCKED
    }
}
