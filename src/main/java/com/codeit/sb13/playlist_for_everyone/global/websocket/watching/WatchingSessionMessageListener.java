package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 현재 시청 수 관련 redis 메세지를 처리하는 리스너.
 * destination 을 통해 같은 컨텐츠를 구독하는 채널에 데이터 전송.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionMessageListener {
}
