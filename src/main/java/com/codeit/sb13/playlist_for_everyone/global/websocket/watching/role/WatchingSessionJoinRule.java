package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionJoinEvent;

import java.util.regex.Pattern;

public class WatchingSessionJoinRule extends DestinationRule<WatchingSessionJoinEvent> {

    public WatchingSessionJoinRule(Pattern pattern){
        super(pattern);
    }

    @Override
    public WatchingSessionJoinEvent run(SessionInfo info) {
        return new WatchingSessionJoinEvent(info);
    }

}
