package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebSocketSubscriptionRegistry {

    WSSubscription add(WSSubscription subscription);

    void remove(WSSubscription subscription);

    void clear();

    Optional<WSSubscription> get(String subscriptionId);
    Optional<WSSubscription> get(UUID subscriptionUUID);
    List<WSSubscription> getAll();
}
