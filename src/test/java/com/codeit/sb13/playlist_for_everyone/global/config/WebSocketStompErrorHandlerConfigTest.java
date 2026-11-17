package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageErrorMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageErrorSenderImpl;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.websocket.DirectMessageStompErrorHandler;
import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolErrorHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        WebSocketConfig.class,
        DirectMessageStompErrorHandler.class,
        DirectMessageErrorSenderImpl.class,
        DirectMessageErrorMapper.class,
        WebSocketStompErrorHandlerConfigTest.TestConfig.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("WebSocket STOMP 오류 처리기 등록")
public class WebSocketStompErrorHandlerConfigTest {

    @Autowired
    @Qualifier("subProtocolWebSocketHandler")
    private WebSocketHandler webSocketHandler;

    @Autowired
    private SubProtocolErrorHandler<byte[]> errorHandler;

    @MockitoBean
    private WebSocketInterceptor webSocketInterceptor;

    @MockitoBean
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @MockitoBean(name = "webSocketAuthorizationManager")
    private AuthorizationManager<Message<?>> authorizationManager;

    @Test
    @DisplayName("Spring이 생성한 STOMP 처리기에 DM 오류 처리기 빈을 등록한다.")
    void stompWebSocketHandler_registersDirectMessageErrorHandler(){
        assertThat(webSocketHandler).isInstanceOf(SubProtocolWebSocketHandler.class);

        SubProtocolWebSocketHandler handler = (SubProtocolWebSocketHandler) webSocketHandler;

        assertThat(handler.getProtocolHandlers())
                .filteredOn(StompSubProtocolHandler.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(StompSubProtocolHandler.class,
                        stompHandler -> assertThat(stompHandler.getErrorHandler())
                                .isSameAs(errorHandler));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig{
        @Bean
        ObjectMapper objectMapper(){
            return JsonMapper.builder().build();
        }
    }
}
