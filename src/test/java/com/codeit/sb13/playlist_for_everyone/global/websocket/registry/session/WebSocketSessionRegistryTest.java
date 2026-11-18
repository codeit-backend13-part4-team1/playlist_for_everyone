package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 웹 소켓을 저장하는 저장용 구조체 테스트
 * 1. 저장은 멱등성이 있는가
 * 2. 삭제는 멱등성이 있는가
 * 3. 유저 정보를 통해 특정 세션을 조회할 수 있는가
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("웹 소켓 저장 구조체 테스트")
public class WebSocketSessionRegistryTest {

    WebSocketSessionRegistry sessionRegistry;

    static String sessionId = "test-session-id";

    @BeforeEach
    void setUp(){
        sessionRegistry = new WebSocketSessionRegistryImpl();
    }

    WSSession wssession(String sessionId, UUID userId, UUID loginId){
        WSSession session = new WSSession(sessionId, mock(WebSocketSession.class));
        session.connect(userId.toString(), loginId.toString());
        return session;
    }

    @Nested
    @DisplayName("파라매터 방어 테스트")
    class Param {

        @Test
        @DisplayName("세션 저장은 null 을 받지 않는다.")
        void saveNullSession(){
            assertThatThrownBy(() -> sessionRegistry.register(null))
                    .isInstanceOf(NullPointerException.class);
        }

    }

    @Nested
    @DisplayName("세션 멱등성 테스트")
    class Idempotency{

        @Test
        @DisplayName("세션 저장은 최초로 저장된 세션이 저장된다.")
        void saveIdempotency(){
            UUID userId = UUID.randomUUID();
            UUID loginId = UUID.randomUUID();

            WSSession session = wssession(sessionId, userId, loginId);
            WSSession wrongSession = wssession(sessionId, UUID.randomUUID(), UUID.randomUUID());

            sessionRegistry.register(session);
            sessionRegistry.register(wrongSession);

            sessionRegistry.query(sessionId).ifPresentOrElse(
                    s -> assertThat(s).isEqualTo(session),
                    () -> fail("세션을 찾을 수 없습니다.")
            );
        }

        @Test
        @DisplayName("세션 삭제는 중복 삭제에도 멱등성이 보장된다.")
        void deleteIdempotency(){
            UUID userId = UUID.randomUUID();
            UUID loginId = UUID.randomUUID();

            WSSession session = wssession(sessionId, userId, loginId);
            sessionRegistry.register(session);

            sessionRegistry.unregister(sessionId);
            sessionRegistry.unregister(sessionId);

            assertThat(sessionRegistry.query(sessionId)).isEmpty();
        }

    }

    @Nested
    @DisplayName("유저 정보를 통한 조회 테스트")
    class Query {

        @Test
        @DisplayName("유저 id 와 로그인 세션 id 로 특정 세션을 조회할 수 있다.")
        void queryByUserIdAndLoginId(){
            UUID userId = UUID.randomUUID();
            UUID loginId = UUID.randomUUID();

            WSSession session = wssession(sessionId, userId, loginId);
            sessionRegistry.register(session);

            assertThat(sessionRegistry.findByUserIdAndLoginSessionId(userId, loginId)).contains(session);
        }

    }

}
