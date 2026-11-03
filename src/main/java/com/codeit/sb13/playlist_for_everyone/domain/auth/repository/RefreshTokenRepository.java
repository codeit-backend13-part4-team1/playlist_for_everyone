package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 *  Refresh Token의 등록 정보를 관리하는 저장소 계약.
 *
 *  JWT 서명 검증과 사용자 상태 확인은 호출하는 서비스 에서 처리한다.
 */
public interface RefreshTokenRepository {

    /**
     * 토큰과 연결된 사용자 ID를 남은 유효시간 동안 저장한다.
     * @param userId 토큰의 대상 사용자 Iㅇ
     * @param refreshToken 저장할 Refresh Token
     * @param ttl 토큰의 남은 유효시간
     * @throws IllegalArgumentException ttl이 0 이하인 경우
     */
    void save(UUID userId, String refreshToken, Duration ttl);


    /**
     * 등록된 토큰에 연결된 사용자 ID를 조회한다.
     *
     * 등록되지 않았거나 만료된 토큰이면 Optional.empty()를 반환한다.
     * 이 조회 자체가 토큰을 소비하거나 삭제하지 않는다.
     */
    Optional<UUID> findUserId(String refreshToken);


    /**
     * 해당 토큰의 등록 정보를 삭제한다.
     *
     * 이미 없는 토큰을 삭제해도 오류가 발생하지 않는다.
     */
    void delete(String refreshToken);

    /**
     *
     * 기존 Refresh Token을 새 토큰으로 원자적으로 교체한다.
     *
     * 기존 토큰이 해당 사용자에게 등록된 경우에만 교체한다.
     * 확인·삭제·저장을 하나의 작업으로 처리하여,
     * 같은 토큰에 대한 동시 재발급 중 하나만 성공하도록 한다.
     *
     * @return 교체에 성공하면 true,
     * 기존 토큰이 없거나 사용자 ID가 다르면 false
     */
    boolean rotate(UUID userId, String oldRefreshToken, String newRefreshToken, Duration ttl);


}
