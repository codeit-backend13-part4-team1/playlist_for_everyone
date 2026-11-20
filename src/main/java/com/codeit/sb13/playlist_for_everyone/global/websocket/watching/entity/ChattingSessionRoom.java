package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity;

import lombok.Getter;

import java.util.UUID;

/**
 * Chatting 세션에서 세션 룸을 나타내는 객체.
 * 룸이 가지고 있는 정보를 담는다.
 */
@Getter
public class ChattingSessionRoom extends SessionRoom{

    private final String messagingPrefix = "websocket:room:watching:chat:";

    public ChattingSessionRoom(UUID contentId) {
        super(contentId);
    }

    public String topic(){ return messagingPrefix + contentId; }

}
