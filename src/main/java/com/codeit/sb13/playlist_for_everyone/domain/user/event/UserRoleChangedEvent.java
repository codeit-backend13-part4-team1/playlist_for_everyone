package com.codeit.sb13.playlist_for_everyone.domain.user.event;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;

import java.util.UUID;

/**
 *  사용자의 권한이 실제로 변경되었음을 알리는 이벤트
 *
 *  업무 트랜잭션 안에서 발행하며, 알림 도메인은 커밋 성공 후 이 이벤트를 처리한다.
 */
public record UserRoleChangedEvent(
        UUID userId,
        Role previousRole,
        Role newRole
) {
}
