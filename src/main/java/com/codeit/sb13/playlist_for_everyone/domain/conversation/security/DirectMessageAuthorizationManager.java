package com.codeit.sb13.playlist_for_everyone.domain.conversation.security;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DirectMessageAuthorizationManager implements AuthorizationManager<MessageAuthorizationContext<?>> {

    private final ConversationService conversationService;

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication, MessageAuthorizationContext<?> object) {
        return new AuthorizationDecision(false);
    }
}
