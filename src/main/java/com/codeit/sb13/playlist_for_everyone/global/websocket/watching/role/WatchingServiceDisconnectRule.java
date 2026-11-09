package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingServiceDisconnectEvent;

public class WatchingServiceDisconnectRule extends SessionRule<WatchingServiceDisconnectEvent> {

    public WatchingServiceDisconnectRule() {
        super(null);
    }

    @Override
    public WatchingServiceDisconnectEvent run(SessionInfo info){
        return new WatchingServiceDisconnectEvent(info);
    }

}
