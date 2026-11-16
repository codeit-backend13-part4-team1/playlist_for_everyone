package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
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

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private TaskScheduler taskScheduler;
    private final WebSocketInterceptor webSocketInterceptor;
    private final WebSocketSessionRegistry socketRegistry;

    /** DM 목적지별 접근 규칙을 가진 매니저이며, 생성자에서 빈 이름을 지정해 주입한다. */
    private final AuthorizationManager<Message<?>> directMessageWebSocketAuthorizationManager;

    public WebSocketConfig(
            WebSocketInterceptor webSocketInterceptor,
            WebSocketSessionRegistry socketRegistry,
            @Qualifier("directMessageWebSocketAuthorizationManager")
            AuthorizationManager<Message<?>> directMessageWebSocketAuthorizationManager) {
        this.webSocketInterceptor = webSocketInterceptor;
        this.socketRegistry = socketRegistry;
        this.directMessageWebSocketAuthorizationManager = directMessageWebSocketAuthorizationManager;
    }

    @Autowired void setTaskScheduler(@Lazy TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
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
     * SecurityContext에 반영한 다음 DM 경로와 대화방 참여 여부를 검증한다.
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

    /*
     * TODO(콘텐츠 담당자와 협의): 콘텐츠 전용 채널 인가 정책을 확정한다.
     * 기존 CONNECT 인증과 콘텐츠 컨트롤러·서비스의 검사는 유지한다.
     * 아래 항목은 추가·보강 후보이며, 적용 범위와 방식은 협의 후 결정한다.
     *
     * - 채팅 SEND와 시청·채팅 SUBSCRIBE의 허용 경로, UUID 형식,
     *   패턴 구독 제한을 채널 단계에서 검증할 범위
     * - 클라이언트의 /sub/contents/** 직접 SEND 등 목적지 방향 우회 차단
     * - 역할·계정 상태·콘텐츠 존재 여부 검증의 채널과 업무 로직 간 역할 분담
     * - 채팅 전송에 해당 콘텐츠의 시청 또는 채팅 구독 참여를 요구할지 여부
     * - 콘텐츠 인가 매니저의 분리 여부와 DM 인가 규칙을 함께 적용할 방식
     *
     * 정책 반영 시 콘텐츠 시청·채팅과 DM의 기존 동작을 함께 회귀 검증한다.
     */
    /**
     * DM 목적지별 인가 매니저를 채널 인터셉터로 연결한다.
     * 경로 선택과 구독 제한은 DirectMessageWebSocketAuthorizationConfig가 담당하고,
     * 해당 규칙에서 대화방 참여 검증을 DirectMessageAuthorizationManager에 위임한다.
     */
    private AuthorizationChannelInterceptor authorizationChannelInterceptor() {
        return new AuthorizationChannelInterceptor(
                directMessageWebSocketAuthorizationManager
        );
    }

}
