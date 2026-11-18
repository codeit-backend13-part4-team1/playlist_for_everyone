package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config;


import java.util.regex.Pattern;

/**
 * watchingSession 관련 설정을 모아둔 클래스.
 */

public class WatchingSessionProperties {

    static public final String PATH_PREFIX = "/sub/contents/";
    static public final String VIEW_POSTFIX = "/watch";
    static public final String CHAT_POSTFIX = "/chat";

    // redis Prefix
    // watchingSession 의 세션 저장소용 prefix
    static public final String STORAGE_SESSION_PREFIX = "storage:watching:session:";
    static public final String STORAGE_CONTENT_PREFIX = "storage:watching:content:";
    // watchingSession 의 데이터(Session change event, chat) 전달용 prefix
    static public final String MESSAGE_CHAT_PREFIX = "message:watching:chat:";
    static public final String MESSAGE_VIEW_PREFIX = "message:watching:view:";

    static public final String UUID_REGEX = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";

    static public Pattern watchingSessionUrlPattern(){
        return Pattern.compile("^" + PATH_PREFIX + UUID_REGEX + VIEW_POSTFIX + "$");
    }

    static public Pattern watchingChatUrlPattern(){
        return Pattern.compile("^" + PATH_PREFIX + UUID_REGEX + CHAT_POSTFIX + "$");
    }

    static public Pattern watchingChatSendUrlPattern(){
        return Pattern.compile("^" + "/pub/contents/" + UUID_REGEX + CHAT_POSTFIX + "$");
    }

}
