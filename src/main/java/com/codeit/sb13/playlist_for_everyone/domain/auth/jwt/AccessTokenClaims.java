package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import java.util.UUID;

/**
 * 서명 만료 종류 검증을 통과한 Access Token의 정보
 *
 * 이 세션이 현재 로그인 세션인지는 호출하는 쪽에서 UserSessionRepository로 확인한다.
 */
public record AccessTokenClaims(
        UUID userId,
        UUID sessionId
) {
}
