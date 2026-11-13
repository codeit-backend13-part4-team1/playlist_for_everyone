package com.codeit.sb13.playlist_for_everyone.global.websocket.service;

/**
 * 웹 소켓 세션의 기본적인 관 매서드를 제공하는 인터페이스
 */
public interface WebSocketService {

    /**
     * Principal 의 유저정보를 받아 해당 유저의
     * 연결된 웹 소켓 세션을 닫는 매서드
     * @param username 인증된 세션들의 유저 이름(이메일).
     */
    void revokeAuth(String username);

}
