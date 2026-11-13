package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;

import java.util.List;
import java.util.Optional;

/**
 * 세션 구독 정보를 반환하는 레지스트리 매서드 계약.
 */
public interface SubscriptionRegistry {

    /**
     * 레지스트리에 세션 - 구독 정보를 등록한다.
     * @param info 현재 요청된 프레임에 대한 정보
     */
    void register(SessionInfo info);

    /**
     * 레지스트리에 저장된 세션 - 구독 정보를 해제한다.
     * @param info 현재 요청된 프레임에 대한 정보
     */
    void unregister(SessionInfo info);

    /**
     * 세션에 속한 모든 구독 정보를 해지한다.
     * @param info 현재 요청된 프레임에 대한 정보
     */
    void disconnect(SessionInfo info);

    /**
     * 저장된 세션에 속한 모든 구독에 대한 info 를 반환한다.
     * @param info 현재 요청된 프레임에 대한 정보
     * @return 세션과 세션에 속한 구독에 대한 SocketConnection 목록
     */
    List<SessionInfo> getAllBySession(SessionInfo info);

    /**
     * 현재 세션 - 구독쌍에 대해 등록된 info 를 반환한다..
     * @param info 현재 요청된 프레임에 대한 정보
     * @return 현재 등록된 세션에 대한 구독 정보.
     */
    Optional<SessionInfo> get(SessionInfo info);
}
