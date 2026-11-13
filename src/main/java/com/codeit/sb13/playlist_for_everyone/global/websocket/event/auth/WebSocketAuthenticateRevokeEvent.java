package com.codeit.sb13.playlist_for_everyone.global.websocket.event.auth;


/**
 * 인증이 만료된 SecurityUser 의 username 을 발신하는 이벤트.
 * 해당 유저의 인증이 만료된 웹 소켓 세션을 강제로 종료하는 이벤트를 트리거 한다.
 * @param username SecurityUser 의 username
 */
public record WebSocketAuthenticateRevokeEvent(
        String username
) {}
