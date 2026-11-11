package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세션 - 구독 정보를 저장하는 인메모리 구현체.
 * 비즈니스 로직에 대한 구독을 관리한다.
 * Broker 에 대한 구독은 관리하지 않는다.
 * 즉, 현재 브로커 메세지에 대한 구독 상태와 상이할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRegistryImpl implements SubscriptionRegistry {

    private final WebSocketRegistry socketRegistry;
    /*
     * 기본 세션 SocketConnection(sessionId,null) 을 키로 구독을 할 때마다 Set 에 엔티티 객체를 추가한다.
     */
    private final Map<String, SessionState> container = new ConcurrentHashMap<>();

    // 등록, 삭제에 문제가 많아지면 추후 측정 후 변경.
    static private class SessionState {

        private boolean creatable = true;
        private Map<String, SessionInfo> map;


        private SessionState() {
            this.map = new HashMap<>();
        }

        private SessionState(Map<String, SessionInfo> map) {
            this.map = map;
        }


        private SessionState subscribe(SessionInfo info) {
            String subscriptionId = Objects.requireNonNull(info.subscription());

            // 이미 존재한다면, 추가 안 함.
            if (map.containsKey(subscriptionId)) return this;

            Map<String, SessionInfo> newMap = new HashMap<>(map);
            newMap.put(subscriptionId, info);
            return new SessionState(newMap);
        }

        private SessionState unsubscribe(SessionInfo info) {
            String subscriptionId = Objects.requireNonNull(info.subscription());

            // 이미 없다면 삭제 안 함.
            if(!map.containsKey(subscriptionId)) return this;

            Map<String, SessionInfo> newMap = new HashMap<>(map);
            newMap.remove(subscriptionId);
            return new SessionState(newMap);
        }

        private SessionInfo subscription(String subscriptionId) {
            if (subscriptionId == null) return null;
            return map.get(subscriptionId);
        }

        private List<SessionInfo> subscriptions() {
            return map.values().stream().toList();
        }
    }

    @Override
    public void register(SessionInfo info) {
        Objects.requireNonNull(info);
        String sessionId = Objects.requireNonNull(info.session());

        container.compute(sessionId, (id, state) -> {
            if (socketRegistry.query(id).isEmpty())
                throw new RuntimeException("존재하지 않는 세션에서는 구독 할 수 없습니다.");

            if (state == null) state = new SessionState();

            state = state.subscribe(info);
            return state;
        });
    }

    @Override
    public void unregister(SessionInfo info){
        Objects.requireNonNull(info);
        String sessionId = Objects.requireNonNull(info.session());

        try {
            container.computeIfPresent(sessionId, (id, state) ->{
                if(socketRegistry.query(sessionId).isEmpty())
                    throw new RuntimeException("존재하지 않는 세션에서는 구독 해제 할 수 없습니다.");
                state = state.unsubscribe(info);
                return state;
            });
        } catch (RuntimeException e) {
            log.warn("[{}] 세션 구독 해제 실패 - 이유 : {}", "SubscriptionRegistry", e.getMessage());
            disconnect(info);
        }
    }

    @Override
    public void disconnect(SessionInfo info){
        Objects.requireNonNull(info);
        String sessionId = Objects.requireNonNull(info.session());
        container.remove(sessionId);
    }

    @Override
    public List<SessionInfo> getAllBySession(SessionInfo info) {
        Objects.requireNonNull(info);
        String sessionId = Objects.requireNonNull(info.session());

        if (socketRegistry.query(sessionId).isEmpty())
            return List.of();

        SessionState state = container.get(sessionId);

        return state == null ? List.of() : state.subscriptions();
    }

    @Override
    public Optional<SessionInfo> get(SessionInfo info) {
        Objects.requireNonNull(info);
        String sessionId = Objects.requireNonNull(info.session());

        String subscriptionId = Objects.requireNonNull(info.subscription());

        if (socketRegistry.query(info.session()).isEmpty())
            throw new RuntimeException("존재하지 않는 세션에 대한 구독은 조회할 수 없습니다.");

        return Optional.ofNullable(container.get(sessionId).subscription(subscriptionId));
    }
}
