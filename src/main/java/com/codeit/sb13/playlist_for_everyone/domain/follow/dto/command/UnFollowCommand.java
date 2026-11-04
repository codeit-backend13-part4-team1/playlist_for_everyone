package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command;

import java.util.UUID;

/**
 * 취소할 관계와 요청자를 함께 전달하는 서비스 입력.
 *
 * @param followId 사용자 ID가 아닌, 삭제할 팔로우 관계의 ID
 * @param followerId HTTP 호출에서는 Principal에서 가져온 요청자 ID. Guard의 소유자 판단에 사용한다.
 */
public record UnFollowCommand(UUID followId, UUID followerId) {
}
