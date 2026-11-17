package com.codeit.sb13.playlist_for_everyone.global.websocket.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 세션 추상 엔티티 WSSession 객체를 테스트 하기 위한 클래스
 */

public class WSSessionTest {

    final String sessionID = "test-session";
    WSSession session;

    @BeforeEach
    void setUp() {
        session = new WSSession(sessionID, mock(WebSocketSession.class));
    }


    @Test
    @DisplayName("WSSession 객체 는 Register 상태가 아니면 구독을 할 수 없음. 이미 같은 구독ID 혹은 경로가 존재하면 에러를 반환함.")
    void testSubscribe(){
        assertThatThrownBy(()-> session.subscribe("test-sub-1", "/"))
                .isInstanceOf(IllegalStateException.class);

        session.connect("test-user", "test-login-session");

        session.subscribe("test-sub-1", "/");

        session.getSubscription("test-sub-1").ifPresentOrElse(
                subscription -> assertThat(subscription.id()).isEqualTo("test-sub-1"),
                () -> fail("구독이 등록되지 않았습니다.")
        );

        assertThatThrownBy(()-> session.subscribe("test-sub-1", "/"))
                .isInstanceOf(IllegalStateException.class);
    }


    @Test
    @DisplayName("WSSession 객체는 Register 상태가 아니면 구독을 해지 할 수 없으나, 없는 구독에 대해선 무시함.")
    void testUnsubscribe() {
        WSSubscription sub;

        // 인자가 없으면 NPE
        assertThatThrownBy(()-> session.unsubscribe(null))
                .isInstanceOf(NullPointerException.class);

        session.connect("test-user", "test-login-session");

        assertThatNoException().isThrownBy(() -> session.unsubscribe(new WSSubscription("test-sub", "/sdf")));

        session.subscribe("test-sub-1", "/");


        session.getSubscription("test-sub-1").ifPresentOrElse(
                subscription -> {
                    assertThat(subscription.id()).isEqualTo("test-sub-1");
                    session.unsubscribe(subscription);
                },
                () -> fail("구독이 등록되지 않았습니다.")
        );

        session.getSubscription("test-sub-1").ifPresentOrElse(
                subscription -> fail("구독이 해지되지 않았습니다."),
                () -> {}
        );

    }

    @Test
    @DisplayName("세션 상태가 register 일 때만 세션 삭제가 가능하고, 세션이 삭제되면 모든 구독이 다 해지된다.")
    void testDisconnect() throws Exception {
        session.connect("test-user", "test-login-session");
        session.subscribe("test-sub-1", "/path1");
        session.subscribe("test-sub-2", "/path2");
        session.subscribe("test-sub-3", "/path3");

        assertThatNoException().isThrownBy(() -> session.disconnect());

        session.getSubscription("test-sub-1").ifPresentOrElse(
                subscription -> fail("구독이 해지되지 않았습니다."),
                () -> {}
        );
    }

}
