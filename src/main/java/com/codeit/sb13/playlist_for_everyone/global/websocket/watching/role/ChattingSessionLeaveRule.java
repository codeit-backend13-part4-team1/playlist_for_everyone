package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingChatLeaveEvent;

import java.util.regex.Pattern;

public class ChattingSessionLeaveRule extends DestinationRule<WatchingChatLeaveEvent> {

    public ChattingSessionLeaveRule(Pattern pattern){
        super(pattern);
    }

    @Override
    public WatchingChatLeaveEvent run(SessionInfo info){
        return new WatchingChatLeaveEvent(info);
    }

}
