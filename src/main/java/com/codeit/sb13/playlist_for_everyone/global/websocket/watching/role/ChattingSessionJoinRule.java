package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatJoinEvent;

import java.util.regex.Pattern;

public class ChattingSessionJoinRule extends DestinationRule<WatchingChatJoinEvent> {

    public ChattingSessionJoinRule(Pattern pattern){
        super(pattern);
    }

    @Override
    public WatchingChatJoinEvent run(SessionInfo info){
        return new WatchingChatJoinEvent(info);
    }
}
