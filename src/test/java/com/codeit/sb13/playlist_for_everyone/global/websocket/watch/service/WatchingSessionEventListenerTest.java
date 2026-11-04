package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * WatchingSession 서비스 이벤트 테스트
 * 채널 인터셉터 로직에서 호출된 이벤트가 실제 서비스로직과 관련 리소스를 호출하는지 확인하는 테스트
 * 구체적으론,
 * - 세션 정보 저장소에서 현재 세션 정보 호출 매서드를 호출하는지.
 * - 이벤트에 알맞는 서비스 매서드를 호출하는지
 * - 이벤트에 알맞는 redis
 * 실제 서비스 로직은 서비스 로직 통합 테스트에서 확인한다.
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSession 서비스 이벤트 테스트")
public class WatchingSessionEventListenerTest {
}
