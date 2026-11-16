package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;

@Configuration(proxyBeanMethods = false)
public class DirectMessageWebSocketAuthorizationConfig {

    @Bean
    public AuthorizationManager<Message<?>> directMessageWebSocketAuthorizationManager(
            @Qualifier("directMessageAuthorizationManager")
            AuthorizationManager<MessageAuthorizationContext<?>> directMessageAuthorizationManager
    ){
        return (authentication, message) ->
        new AuthorizationDecision(false);
    }
}
