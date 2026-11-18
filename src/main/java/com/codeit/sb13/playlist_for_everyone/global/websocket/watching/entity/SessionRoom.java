package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class SessionRoom {
    protected final UUID id;
    protected final UUID contentId;
    // 만약 추후 UUID 에서 구독 반환이 필요하면 map 으로 변경.
    protected final Set<WSSubscription> subscriptions = new CopyOnWriteArraySet<>();

    public SessionRoom(UUID contentId) {
        id = UUID.randomUUID();
        this.contentId = contentId;
    }

    abstract String topic();
    public UUID id(){ return id; }
    public UUID contentId(){ return contentId; }

    public void join(WSSubscription subscription){
      subscriptions.add(subscription);
    }

    public void leave(WSSubscription subscription){
        subscriptions.remove(subscription);
    }

    public void leaveAll(List<WSSubscription> subscriptionIds){
        subscriptionIds.forEach(subscriptions::remove);
    }

    public void clear(){ subscriptions.clear(); }

    public List<WSSubscription> getAll(){ return subscriptions.stream().toList();}

}
