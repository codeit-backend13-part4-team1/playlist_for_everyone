package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private TaskScheduler taskScheduler;
    private final WebSocketInterceptor webSocketInterceptor;
    private final WebSocketSessionRegistry socketRegistry;

    /** 메시지 종류와 목적지별 접근 규칙을 가진 공통 인가 매니저를 빈 이름으로 지정해 주입한다. */
    private final AuthorizationManager<Message<?>> webSocketAuthorizationManager;
    private final ObjectProvider<StompSubProtocolErrorHandler> stompErrorHandlerProvider;

    public WebSocketConfig(
            WebSocketInterceptor webSocketInterceptor,
            WebSocketSessionRegistry socketRegistry,
            @Qualifier("webSocketAuthorizationManager")
            AuthorizationManager<Message<?>> webSocketAuthorizationManager,
            @Qualifier("webSocketStompErrorHandler")
            ObjectProvider<StompSubProtocolErrorHandler> stompErrorHandlerProvider) {
        this.webSocketInterceptor = webSocketInterceptor;
        this.socketRegistry = socketRegistry;
        this.webSocketAuthorizationManager = webSocketAuthorizationManager;
        this.stompErrorHandlerProvider = stompErrorHandlerProvider;
    }

    @Autowired void setTaskScheduler(@Lazy TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(stompErrorHandlerProvider.getObject());
        registry.addEndpoint("/ws");
        registry.addEndpoint("/ws").withSockJS();   // 레거시
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(taskScheduler);
        registry.setApplicationDestinationPrefixes("/pub");
    }

    /**
     * 세션을 사용자 코드에서 임의로 관리하기 위한 데코레이터 설정.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(
            handler -> new WebSocketHandlerDecorator(handler){
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    WSSession ws = new WSSession(session.getId(), session);
                    socketRegistry.register(ws);
                    try {
                        super.afterConnectionEstablished(session);
                    } catch (Exception e) {
                        socketRegistry.unregister(session.getId());
                        throw e;
                    }
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    try {
                        super.afterConnectionClosed(session, closeStatus);
                    } finally {
                        socketRegistry.unregister(session.getId());
                    }
                }
            }
        );
    }

    /**
     * 인증 정보를 준비한 뒤 목적지별 인가를 수행하도록 인바운드 인터셉터를 연결한다.
     *
     * <p>CONNECT의 JWT 검증으로 Principal을 설정하고, 각 메시지의 인증 정보를
     * SecurityContext에 반영한 다음 메시지 종류와 목적지별 인가 규칙을 적용한다.
     * DM 경로에서는 해당 규칙에 따라 대화방 참여 여부도 검증한다.
     * 이 순서를 유지해야 인가 매니저가 요청자의 인증 정보를 사용할 수 있다.
     *
     * <p>콘텐츠 시청·채팅 경로와 연결 관리 메시지는 DM 참여 검증 없이 통과한다.
     * CONNECT의 JWT 인증과 콘텐츠 컨트롤러의 기존 권한 검사는 각 담당 위치에서 유지된다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                webSocketInterceptor,
                new SecurityContextChannelInterceptor(),
                authorizationChannelInterceptor()
        );
    }

    /**
     * WebSocketAuthorizationConfig의 공통 인가 매니저를 채널 인터셉터로 연결한다.
     * 목적지별 정책은 해당 설정에서 구성하고, 이곳에서는 인터셉터의 연결 순서를 관리한다.
     */
    private AuthorizationChannelInterceptor authorizationChannelInterceptor() {
        return new AuthorizationChannelInterceptor(
                webSocketAuthorizationManager
        );
    }

}
