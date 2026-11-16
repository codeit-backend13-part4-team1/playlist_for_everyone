package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration(proxyBeanMethods = false)
public class DirectMessageWebSocketAuthorizationConfig {

    @Bean
    public AuthorizationManager<Message<?>> directMessageWebSocketAuthorizationManager(
            @Qualifier("directMessageAuthorizationManager")
            AuthorizationManager<MessageAuthorizationContext<?>> directMessageAuthorizationManager
    ){
        return MessageMatcherDelegatingAuthorizationManager.builder()
                .simpTypeMatchers(
                        SimpMessageType.CONNECT,
                        SimpMessageType.HEARTBEAT,
                        SimpMessageType.UNSUBSCRIBE,
                        SimpMessageType.DISCONNECT
                ).permitAll()
                .simpMessageDestMatchers(
                        "/pub/conversations/{conversationId}/direct-messages"
                ).access(directMessageAuthorizationManager)
                .simpSubscribeDestMatchers(
                        "/sub/conversations/{conversationId}/direct-messages"
                ).access(directMessageAuthorizationManager)
                .simpDestMatchers(
                        "/pub/conversations/**",
                        "/sub/conversations/**"
                ).denyAll()
                .simpSubscribeDestMatchers(
                        "/sub/contents/**"
                ).permitAll()
                .simpTypeMatchers(SimpMessageType.SUBSCRIBE)
                .denyAll()
                .anyMessage().permitAll()
                .build();
    }
}
