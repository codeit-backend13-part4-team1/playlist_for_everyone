package com.codeit.sb13.playlist_for_everyone.global.websocket.registry;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionData;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 세션 정보를 저장하는 인메모리 구현체.
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryConnectionRegistry implements ConnectionRegistry {

    private final WebSocketRegistry socketRegistry;
    /*
     * 기본 세션 SocketConnection(sessionId,null) 을 키로 구독을 할 때마다 Set 에 엔티티 객체를 추가한다.
     */
    private final Map<String, SessionState> container = new ConcurrentHashMap<>();

    // 등록, 삭제에 문제가 많아지면 추후 측정 후 변경.
    static private class SessionState{
        private final SessionData session;
        private final Map<String, SessionData> subscriptions;

        public SessionState(SessionData session){
            this.session = session;
            this.subscriptions = new HashMap<>();
        }

        // 내부 카피용 매서드
        private SessionState(SessionData session, Map<String, SessionData> subscriptions){
            this.session = session;
            this.subscriptions = subscriptions;
        }

        public SessionState subscribe(SessionData subscription){
            Map<String, SessionData> newMap = new HashMap<>(subscriptions);
            newMap.put(subscription.subscription(), subscription);
            return new SessionState(session, newMap);
        }

        public SessionState unsubscribe(SessionData subscription){
            Map<String, SessionData> newMap = new HashMap<>(subscriptions);
            newMap.remove(subscription.subscription());
            return new SessionState(session, newMap);
        }

        public SessionData session(){
            return session;
        }

        public SessionData subscription(String subscriptionId){
            return subscriptions.get(subscriptionId);
        }

        public List<SessionData> all(){
            return Stream.concat(Stream.of(session), subscriptions.values().stream()).toList();
        }
    }

    @Override
    public void register(String sessionId, @Nullable Principal principal) {
        register(sessionId, null, principal);
    }

    @Override
    public void register(String sessionId, @Nullable String subscriptionId, @Nullable Principal principal) {
        register(SessionData.builder()
                .session(sessionId)
                .subscription(subscriptionId)
                .principal(principal)
                .build());
    }

    @Override
    public void register(SessionData data){
        Objects.requireNonNull(data);
        try {
            String sessionId = Objects.requireNonNull(data.session());
            String subscriptionId = data.subscription();

            if (subscriptionId == null){
                // data = 세션 only => connection
                container.compute(sessionId,(key, session) -> {
                    if (session != null) throw new NullPointerException("이미 존재하는 세션");
                    return new SessionState(data);
                });
            } else {
                // data = 세션 + 구독 => subscribe
                container.compute(sessionId,(key, session) -> {
                    if (session == null) throw new NullPointerException("존재하지 않는 세션");
                    return session.subscribe(data);
                });
            }
        } catch (NullPointerException e) {
            log.error("[{}] session : {} (subscription : {}) 세션 등록 에러",
                    this.getClass().getSimpleName(),
                    data.session(),
                    data.subscription(),
                    e
            );
            throw new WebSocketSessionRegisterException(e);
        }
    }

    @Override
    public void unregister(String sessionId) {
        unregister(sessionId, null);
    }

    @Override
    public void unregister(String sessionId, @Nullable String subscriptionId) {
        unregister(SessionData.builder()
                .session(sessionId)
                .subscription(subscriptionId)
                .build());
    }

    @Override
    public void unregister(SessionData data) {
        Objects.requireNonNull(data);
        try {
            String sessionId = Objects.requireNonNull(data.session());
            String subscriptionId = data.subscription();

            if (subscriptionId == null){
                // 세션 only == disconnect
                container.remove(sessionId);
            } else {
                // 세션 + 구독 == unsubscribe
                container.computeIfPresent(sessionId,(key,state) ->
                        Objects.requireNonNull(state).unsubscribe(data));
            }
        } catch (NullPointerException e) {
            log.error("[{}] session : {} (subscription : {}) 세션 등록 취소 에러",
                    this.getClass().getSimpleName(),
                    data.session(),
                    data.subscription(),
                    e
            );
            throw new WebSocketSessionRegisterException(e);
        }
    }


    @Override
    public void close(String sessionId) {
        try {
            socketRegistry.close(sessionId);
        } catch (Exception e) {
            log.error("[{}] session : {} 세션 종료 에러",
                    this.getClass().getSimpleName(),
                    sessionId,
                    e
            );
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<SessionData> get(String sessionId) {
        Objects.requireNonNull(sessionId);
        try{
            return container.get(Objects.requireNonNull(sessionId)).all();
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public SessionData get(String sessionId, @Nullable String subscriptionId) {
        String key = Objects.requireNonNull(sessionId);
        try{
            SessionState state = Objects.requireNonNull(container.get(key));
            if (subscriptionId == null) {
                // 세션을 조회
                return state.session();
            } else {
                // 구독을 조회
                return state.subscription(subscriptionId);
            }
        } catch (NullPointerException e) {
            return null;
        }
    }
}
