package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private TaskScheduler taskScheduler;
    private final WebSocketInterceptor webSocketInterceptor;

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
        registry.setApplicationDestinationPrefixes("/pub","/sub");
    }

    // todo - 인증이 생기면, Security, authorize 인터셉터 활성
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                webSocketInterceptor
//                new SecurityContextChannelInterceptor(),
//                authorizationChannelInterceptor()
        );
    }

    // rolehierachy 를 이용한 소켓 권한 검사.
    private AuthorizationChannelInterceptor authorizationChannelInterceptor() {

        AuthorityAuthorizationManager<MessageAuthorizationContext<?>> userManager =
                AuthorityAuthorizationManager.hasRole("Role");

        // Security 설정의 roleHierachy 를 사용하도록 수정
//        userManager.setRoleHierarchy(roleHierarchy);

        return new AuthorizationChannelInterceptor(
                MessageMatcherDelegatingAuthorizationManager.builder()
                        // 연결끊기 및 구독 해제는 전원 허용.
                        .simpTypeMatchers(
                                SimpMessageType.DISCONNECT,
                                SimpMessageType.UNSUBSCRIBE
                        ).permitAll()
                        // 나머진 유저권한 확인.
                        .anyMessage().access(userManager)
                        .build()
        );
    }

}
