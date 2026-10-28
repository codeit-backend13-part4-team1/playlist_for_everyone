package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Event {
    NOTIFICATIONS("notifications"), DIRECT_MESSAGES("direct-messages");

    private final String name;

}
