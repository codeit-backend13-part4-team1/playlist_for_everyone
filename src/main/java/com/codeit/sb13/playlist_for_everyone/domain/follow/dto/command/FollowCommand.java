package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command;

import java.util.UUID;

/**
 * 등록과 내 관계 조회에서 사용하는 서비스 입력.
 *
 * @param followeeId 클라이언트가 지정한 대상 사용자 ID
 * @param followerId HTTP 호출에서는 Principal에서 가져온 요청자 ID
 */
public record FollowCommand(UUID followeeId, UUID followerId) {
}
