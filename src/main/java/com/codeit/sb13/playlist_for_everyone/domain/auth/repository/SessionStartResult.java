package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 로그인 세션 시작 결과.
 *
 * @param sessionId 새로 시작한 로그인 세션 ID
 * @param replacedSessionId 새 로그인으로 교체되어 무효가 된 이전 세션 ID, 첫 로그인이면 null
 */
public record SessionStartResult(
        UUID sessionId,
        UUID replacedSessionId
) {

    public SessionStartResult{
        Objects.requireNonNull(sessionId, "세션 ID는 필수 입니다.");
    }

    /**
     * 중복 로그인으로 교체된 세션이 있으면 반환한다.
     */
    public Optional<UUID> replaced() {
        return Optional.ofNullable(replacedSessionId);
    }
}
