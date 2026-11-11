package com.codeit.sb13.playlist_for_everyone.domain.user.event;

import java.util.Objects;
import java.util.UUID;

/**
 * 관리자가 사용자 계정을 잠갔음을 알리는 이벤트.
 *
 * 업무 트랜잭션 안에서 발행하며, 인증 도메인은 커밋 성공 후 해당 사용자의 로그인 세션을 종료한다.
 * 잠금 해제는 세션을 종료할 이유가 없으므로 발행하지 않는다.
 */
public record UserLockedEvent(
        UUID userId
) {
    public UserLockedEvent {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
    }
}
