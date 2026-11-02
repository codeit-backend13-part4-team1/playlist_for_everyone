package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionJoinEvent;

import java.util.regex.Pattern;

public class WatchingSessionJoinRule extends DestinationRule<WatchingSessionJoinEvent> {

    public WatchingSessionJoinRule(Pattern pattern){
        super(pattern);
    }

    @Override
    public WatchingSessionJoinEvent run(SessionInfo info) {
        String destination = info.destination();
        String sessionId = info.session();
        String subscriptionId = info.subscription();
        SecurityUser user = info.user();

        return new WatchingSessionJoinEvent(
                destination,
                sessionId,
                subscriptionId,
                user
        );
    }

}
