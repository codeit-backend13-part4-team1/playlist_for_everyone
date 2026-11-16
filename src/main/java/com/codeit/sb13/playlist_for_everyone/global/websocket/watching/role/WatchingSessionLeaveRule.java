package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionLeaveEvent;

import java.util.regex.Pattern;

public class WatchingSessionLeaveRule extends DestinationRule<WatchingSessionLeaveEvent> {

    public WatchingSessionLeaveRule(Pattern pattern){
        super(pattern);
    }

    @Override
    public WatchingSessionLeaveEvent run(SessionInfo info){
        return new WatchingSessionLeaveEvent(info);
    }
}
