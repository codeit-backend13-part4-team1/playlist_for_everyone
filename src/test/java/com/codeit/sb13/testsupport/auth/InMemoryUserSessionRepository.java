package com.codeit.sb13.testsupport.auth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 로그인 세션 저장소.
 *
 * Redis가 없는 통합 테스트에서 RedisUserSessionRepository 대신 사용한다.
 * 사용자당 하나의 세션만 유지하는 규칙은 같고, TTL(만료)은 다루지 않는다.
 */
public class InMemoryUserSessionRepository implements UserSessionRepository {

    // 사용자 ID -> 현재 세션 ID
    // 동시 요청 테스트에서도 안전하도록 ConcurrentHashMap을 사용한다.
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();

    @Override
    public SessionStartResult start(UUID userId, Duration ttl) {
        UUID sessionId = UUID.randomUUID();

        UUID previous = sessions.put(userId, sessionId);

        return new SessionStartResult(sessionId, previous);
    }

    @Override
    public boolean isCurrent(UUID userId, UUID sessionId) {
        if (userId == null || sessionId == null) {
            return false;
        }

        return sessionId.equals(sessions.get(userId));
    }

    @Override
    public boolean extendIfCurrent(UUID userId, UUID sessionId, Duration ttl) {
        // TTL을 다루지 않으므로 현재 세션인지만 반환한다.
        return isCurrent(userId, sessionId);
    }

    @Override
    public void endIfCurrent(UUID userId, UUID sessionId) {
        // remove(key, value): 값이 일치할 때만 삭제한다. Lua 비교 후 삭제와 같은 의미다.
        sessions.remove(userId, sessionId);
    }

    @Override
    public Optional<UUID> revoke(UUID userId) {
        return Optional.ofNullable(sessions.remove(userId));
    }
}
