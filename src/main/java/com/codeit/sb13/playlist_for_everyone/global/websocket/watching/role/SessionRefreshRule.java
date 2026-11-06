package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionRefreshEvent;


public class SessionRefreshRule extends SessionRule<WatchingSessionRefreshEvent>{

    public SessionRefreshRule(){
        super("dummy");
    }

    @Override
    public WatchingSessionRefreshEvent run(SessionInfo source) {
        return new WatchingSessionRefreshEvent(source);
    }
}
