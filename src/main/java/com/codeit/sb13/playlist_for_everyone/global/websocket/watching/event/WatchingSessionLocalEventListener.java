package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "websocket.event.multiple", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class WatchingSessionLocalEventListener {

    private final WatchingSession watchingSession;

    @EventListener
    public void onContentsDelete(WatchingSessionContentDeleteEvent event){
        UUID contentId = Objects.requireNonNull(event.contentId());
        watchingSession.disconnectWithContentId(contentId);
    }
}
