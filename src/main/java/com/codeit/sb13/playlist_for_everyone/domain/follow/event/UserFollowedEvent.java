package com.codeit.sb13.playlist_for_everyone.domain.follow.event;

import java.util.UUID;

/**
 * 신규 팔로우 저장 트랜잭션에서 발행하는 도메인 이벤트.
 *
 * <p>발행 시점은 커밋 전이다. 커밋된 관계만 처리할 수신자는 AFTER_COMMIT 리스너를 사용해야 한다.
 * 이벤트에 엔티티를 담지 않으며, 이후 사용자 이름이 변경돼도 발행 당시 문자열을 유지한다.
 * 현재 이름의 null·공백을 보정하지 않고 전달한다. 알림 생성·SSE 전송은 별도 연동 범위다.
 *
 * @param followerId 팔로우한 사용자 ID
 * @param followerName 저장 트랜잭션에서 조회한 팔로워 이름
 * @param followeeId 팔로우받은 사용자 ID
 */
public record UserFollowedEvent(UUID followerId, String followerName, UUID followeeId) {
}
