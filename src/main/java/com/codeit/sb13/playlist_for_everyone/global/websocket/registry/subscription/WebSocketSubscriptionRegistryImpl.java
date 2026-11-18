package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketSubscriptionRegistryImpl implements WebSocketSubscriptionRegistry{

    private final Map<String, WSSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<UUID, String> index = new ConcurrentHashMap<>();

    /** UUID 충돌의 가능성은 무시한다 */
    @Override
    public synchronized WSSubscription add(WSSubscription subscription) {
        Objects.requireNonNull(subscription);
        subscriptions.compute(subscription.id(),(k, v) -> {
            if (v == null) return subscription;
            throw new IllegalStateException("이미 같은 구독 ID 혹은 경로가 존재합니다.");
        });
        // INDEX 는 무조건 갱신
        index.put(subscription.uuid(), subscription.id());
        return subscription;
    }

    @Override
    public synchronized void remove(WSSubscription subscription) {
        subscriptions.computeIfPresent(subscription.id(), (k, v) -> {
            if (v == subscription) {
                index.remove(subscription.uuid(),subscription.id());
                return null;
            }
            return v;
        });
    }

    @Override
    public synchronized void clear(){
        subscriptions.clear();
        index.clear();
    }

    /** 조회 때문에 구독, 구독 취소에 딜레이가 생긴다면, data map copyOnWrite 방식으로 변경 */
    @Override
    public synchronized Optional<WSSubscription> get(String subscriptionId) {
        return Optional.ofNullable(subscriptions.get(subscriptionId));
    }

    /** 조회 때문에 구독, 구독 취소에 딜레이가 생긴다면, data map copyOnWrite 방식으로 변경 */
    @Override
    public synchronized Optional<WSSubscription> get(UUID subscriptionUUID) {
        return Optional.ofNullable(subscriptions.get(index.get(subscriptionUUID)));
    }

    @Override
    public synchronized List<WSSubscription> getAll() {
        return new ArrayList<>(subscriptions.values());
    }
}
