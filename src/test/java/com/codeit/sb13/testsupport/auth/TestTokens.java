package com.codeit.sb13.testsupport.auth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;

import java.time.Duration;
import java.util.UUID;

/**
 * 통합 테스트에서 로그인 API를 거치지 않고 유효한 Access Token을 발급한다.
 *
 * 로그인 성공 처리와 같은 순서로 세션을 시작한 뒤, 그 세션 ID로 토큰을 만든다.
 * 같은 사용자로 다시 호출하면 이전 토큰은 무효가 된다(중복 로그인과 같은 동작).
 */
public final class TestTokens {

    private static final Duration SESSION_TTL = Duration.ofHours(1);

    private TestTokens() {}

    public static String accessToken(JwtTokenProvider jwtTokenProvider,
                                     UserSessionRepository userSessionRepository,
                                     UUID userId) {
        UUID sessionId = userSessionRepository.start(userId, SESSION_TTL).sessionId();

        return jwtTokenProvider.createAccessToken(userId, sessionId);
    }



}
