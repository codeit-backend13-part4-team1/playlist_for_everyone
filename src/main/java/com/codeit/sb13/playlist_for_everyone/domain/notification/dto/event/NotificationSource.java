package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event;

/** 알림 발행 시도와 처리 시간에 공통으로 사용하는 업무 이벤트 출처. */
public enum NotificationSource {
    USER_FOLLOWED("user_followed"),
    USER_ROLE_CHANGED("user_role_changed"),
    PLAYLIST_SUBSCRIBED("playlist_subscribed"),
    PLAYLIST_CREATED("playlist_created"),
    PLAYLIST_CONTENT_ADDED("playlist_content_added"),
    DIRECT_MESSAGE_CREATED("direct_message_created"),
    WATCHING_SESSION_STARTED("watching_session_started"),
    DIRECT_REQUEST("direct_request");

    private final String tagValue;

    NotificationSource(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
