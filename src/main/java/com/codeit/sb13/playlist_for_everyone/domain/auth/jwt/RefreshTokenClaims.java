package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import java.util.UUID;

/**
 * 서명·만료·종류·식별자 검증을 통과한 Refresh Token의 정보.
 *
 * Redis에 등록된 토큰인지, 현재 로그인 세션인지는 서비스에서 확인한다.
 */
public record RefreshTokenClaims(
        UUID userId,
        UUID sessionId
) {
}
