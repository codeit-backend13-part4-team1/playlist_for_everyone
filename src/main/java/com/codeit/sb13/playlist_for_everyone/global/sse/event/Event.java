package com.codeit.sb13.playlist_for_everyone.global.sse.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프론트엔드가 구독하는 업무 이벤트 이름이다.
 * enum 상수명이 아니라 name 값을 SSE의 event 필드에 기록한다.
 * 최초 연결과 하트비트 주석은 업무 이벤트가 아니므로 이 목록에 포함하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum Event {
    NOTIFICATIONS("notifications"), DIRECT_MESSAGES("direct-messages");

    private final String name;

}
