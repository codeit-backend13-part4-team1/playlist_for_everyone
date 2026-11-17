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

/**
 * 정확한 DM SEND·SUBSCRIBE 경로에 매칭된 요청의 인증 객체와 대화방 참여 여부를 확인한다.
 * 경로 매칭·다른 목적지 차단은 WebSocketAuthorizationConfig가 담당한다.
 *
 * <p>미인증·비활성·잠금 사용자, 비정규 UUID, 미존재·비참여 대화방은 거절한다.
 * DB 장애 같은 예상하지 않은 예외를 권한 부족으로 바꾸지는 않는다.
 * CONNECT 때 설정된 인증 정보를 사용하며, 이곳에서 JWT 만료나 Redis 로그인 세션을 재검증하지 않는다.
 */
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

    /** UUID 파싱만으로 허용되는 축약 표현까지 받지 않도록 정규 문자열과 다시 비교한다. 대소문자는 허용한다. */
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
