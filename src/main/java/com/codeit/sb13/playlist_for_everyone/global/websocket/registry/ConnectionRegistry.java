package com.codeit.sb13.playlist_for_everyone.global.websocket.registry;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionData;
import jakarta.annotation.Nullable;

import java.security.Principal;
import java.util.List;

/*
 * 연결 된 세션 관리 레지스트리.
 */
public interface ConnectionRegistry {
    /**
     * 구독 ID 가 없는 세션에 대한 편의 등록 매서드.
     * @param sessionId 고유한 세션 ID
     * @param principal 세션에 대한 인증 정보
     */
    void register(String sessionId, @Nullable Principal principal);

    /**
     * 세션 - 구독 쌍에 대한 등록 매서드.
     * @param sessionId 고유한 세션 ID
     * @param subscriptionId 해당 세션에서 사용된 구독 ID
     * @param principal 세션에 대한 인증 정보
     */
    void register(String sessionId, @Nullable String subscriptionId, @Nullable Principal principal);

    void register(SessionData data);

    /**
     * 구독 ID 가 없는 세션에 대한 편의 등록 해제 매서드. 세션에 해당되는 모든 구독 ID 도 해지된다.
     * @param sessionId 고유한 세션 ID
     */
    void unregister(String sessionId);

    /**
     * 세션 - 구독 쌍에 대한 등록 해제 매서드.
     * @param sessionId 고유한 세션 ID
     * @param subscriptionId 해당 세션에서 사용된 구독 ID
     */
    void unregister(String sessionId, @Nullable String subscriptionId);

    void unregister(SessionData data);

    void close(String sessionId);

    /**
     * 저장된 세션 과 세션에 속한 구독에 대한 SocketConnection 을 반환한다.
     * @param sessionId 세션 ID
     * @return 세션과 세션에 속한 구독에 대한 SocketConnection 목록
     */
    List<SessionData> get(String sessionId);

    /**
     * 세션 - 구독 쌍에 대한 SocketConnection 을 반환한다.
     * @param sessionId 세션 ID
     * @param subscriptionId 구독 ID
     * @return 세션 - 구독 쌍에 대한 SocketConnection
     */
    SessionData get(String sessionId, @Nullable String subscriptionId);
}
