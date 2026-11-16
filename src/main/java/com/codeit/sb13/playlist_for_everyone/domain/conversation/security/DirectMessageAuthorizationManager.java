package com.codeit.sb13.playlist_for_everyone.domain.conversation.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DirectMessageAuthorizationManager implements AuthorizationManager<MessageAuthorizationContext<?>> {

    private final ConversationService conversationService;

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication, MessageAuthorizationContext<?> object) {
        Optional<SecurityUser> securityUser = resolveSecurityUser(authentication.get());
        if (securityUser.isEmpty()){
            return new AuthorizationDecision(false);
        }

        Optional<UUID> resolvedConversationId = resolveConversationId(object.getVariables().get("conversationId"));
        if (resolvedConversationId.isEmpty()){
            return new AuthorizationDecision(false);
        }

        UUID conversationId = resolvedConversationId.get();
        UUID requesterId = securityUser.get().getUserId();
        try {
            conversationService.validateParticipant(conversationId, requesterId);
        } catch (ConversationNotFoundException e){
            return new AuthorizationDecision(false);
        }

        return new AuthorizationDecision(true);
    }

    private Optional<SecurityUser> resolveSecurityUser(Authentication currentAuthentication){
        if (currentAuthentication == null || !currentAuthentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (!(currentAuthentication.getPrincipal() instanceof SecurityUser user)) {
            return Optional.empty();
        }

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    private Optional<UUID> resolveConversationId(String rawConversationId){
        if (rawConversationId == null){
            return Optional.empty();
        }
        UUID conversationId;
        try {
            conversationId = UUID.fromString(rawConversationId);
        }catch (IllegalArgumentException e){
            return Optional.empty();
        }

        if (!conversationId.toString().equalsIgnoreCase(rawConversationId)){
            return Optional.empty();
        }

        return Optional.of(conversationId);
    }
}
