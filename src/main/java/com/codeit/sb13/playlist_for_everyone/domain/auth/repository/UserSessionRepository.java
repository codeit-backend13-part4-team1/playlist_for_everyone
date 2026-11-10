package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자당 하나의 활성 로그인 세션을 관리하는 저장소 계약
 *
 * 새 로그인은 기존 세션을 교체하여 이전 로그인에서 발급한 토큰을 무효화한다(중복 로그인 차단).
 * 세션을 무효화하면 해당 로그인에서 발급한 모든 토큰이 거부된다(강제 로그아웃).
 *
 * SSE·웹소켓 등 연결을 유지하는 기능은 isCurrent()로
 * 연결이 속한 로그인 세션의 유효성을 확인할 수 있다.
 */
public interface UserSessionRepository {

    /**
     * 새 로그인 세션을 시작한다.
     *
     * 기존 세션이 있으면 교체하고, 교체된 세션 ID를 함께 반환한다.
     * 조회와 교체는 원자적으로 처리한다.
     *
     * @throws IllegalArgumentException ttl이 1밀리초 미만인 경우
     */
    SessionStartResult start(UUID userId, Duration ttl);

    /**
     *
     * 전달받은 세션이 해당 사용자의  현재 활성 세션인지 확인한다.
     */
    boolean isCurrent(UUID userId, UUID sessionId);


    /**
     * 현재 세션일 때만 유효기간을 연장한다. 토큰 재발급 시 사용한다.
     *
     * @return 연장 했다면 true, 이미 교체, 종료된 세션이면 false
     */
    boolean extendIfCurrent(UUID userId, UUID sessionId, Duration ttl);

    /**
     *
     * 현재 세션일 때만 종료한다. 로그아웃 시 사용
     * 다른 곳에서 이미 새로 로그인 했다면 그 세션은 유지한다.
     */
    void endIfCurrent(UUID userId, UUID sessionId);

    /**
     * 사용자의 세션을 무조건 종료하고, 종료한 세션 ID를 반환한다.
     * 권한 변경, 계정 잠금시 사용
     *
     * @return 종료한 세션 ID. 활성 세션이 없었다면 빈 값
     */
    Optional<UUID> revoke(UUID userId);


}
