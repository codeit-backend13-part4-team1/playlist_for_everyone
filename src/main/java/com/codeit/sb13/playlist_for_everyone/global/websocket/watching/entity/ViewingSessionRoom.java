package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity;

import lombok.Getter;

import java.util.UUID;

/**
 * 세션 룸을 나타내는 객체.
 * 실질적으로 여러 구독을 room 이라는 토픽에 묶어서 등록하고,
 * 각 room 에 대한 정보(room 의 id, 이름, 구성 정보) 를 저장하는 객체.
 * 메세지 전송을 위한 정보 역시 저장한다.
 * 다만, 메세지 전송 작업 자체는 별도 클래스에서 시행된다.
 */
@Getter
public class ViewingSessionRoom {
    private final UUID id;
    private final UUID contentId;
    private final String messagingPrefix = "websocket:room:watching:view:";

    public ViewingSessionRoom(UUID contentId) {
        this.id = UUID.randomUUID();
        this.contentId = contentId;
    }

    public String topic() {
        return messagingPrefix + contentId;
    }

}
