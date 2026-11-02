package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionLeaveEvent;

import java.util.regex.Pattern;

public class WatchingSessionLeaveRule extends DestinationRule<WatchingSessionLeaveEvent> {

    public WatchingSessionLeaveRule(Pattern pattern){
        super(pattern);
    }

    @Override
    public WatchingSessionLeaveEvent run(SessionInfo info){
        String destination = info.destination();
        String sessionId = info.session();
        String subscriptionId = info.subscription();
        SecurityUser user = info.user();
        // todo - user 정보 추가.
        return new WatchingSessionLeaveEvent(
                destination,
                sessionId,
                subscriptionId
//                user
        );
    }
}
