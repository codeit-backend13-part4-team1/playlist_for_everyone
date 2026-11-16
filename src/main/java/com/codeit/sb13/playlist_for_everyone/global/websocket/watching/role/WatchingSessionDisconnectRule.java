package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionDisconnectEvent;

public class WatchingSessionDisconnectRule extends SessionRule<WatchingSessionDisconnectEvent> {

    public WatchingSessionDisconnectRule() {
        super(null);
    }

    @Override
    public WatchingSessionDisconnectEvent run(SessionInfo info){
        return new WatchingSessionDisconnectEvent(info);
    }

}
