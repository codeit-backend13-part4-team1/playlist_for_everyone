package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config;


/**
 * watchingSession 관련 설정을 모아둔 클래스.
 */

public class WatchingSessionProperties {

    // redis Prefix
    // watchingSession 의 세션 저장소용 prefix
    public static final String STORAGE_SESSION_PREFIX = "storage:watching:session:";
    public static final String STORAGE_CONTENT_PREFIX = "storage:watching:content:";
    // watchingSession 의 데이터(Session change event, chat) 전달용 prefix
    public static final String MESSAGE_CHAT_PREFIX = "message:watching:chat:";
    public static final String MESSAGE_VIEW_PREFIX = "message:watching:view:";

}
