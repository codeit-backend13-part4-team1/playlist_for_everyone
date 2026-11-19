package com.codeit.sb13.playlist_for_everyone.global.websocket.watch;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.ChangeType;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.*;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatSendEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.MESSAGE_CHAT_PREFIX;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.MESSAGE_VIEW_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

/**
 * WatchingSession 서비스 이벤트 테스트
 * 채널 인터셉터 로직에서 호출된 이벤트가 실제 서비스로직과 관련 리소스를 호출하는지 확인하는 테스트
 * 구체적으론,
 * - 이벤트에 알맞는 서비스 매서드를 호출하는지
 * - 이벤트에 알맞는 redis 토픽으로 메세지를 발행하는지.
 * 실제 서비스 로직은 서비스 로직 통합 테스트에서 확인한다.
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSession 서비스 이벤트 테스트")
public class WatchingSessionEventListenerTest {

    @Mock
    WatchingSessionService watchingSessionService;
    @Mock
    ViewingSessionService viewingSessionService;
    @Mock
    ChattingSessionService chattingSessionService;

    @InjectMocks
    WatchingSessionEventListener eventListener;


    /**
     * 테스트용 SessionInfo 생성 편의 매서드
     */

    SessionInfo getSessionInfo(
            String sessionId,
            String subscriptionId,
            String destination,
            Role role
    ){
        return getSessionInfo(sessionId, subscriptionId, destination, role, false);
    }

    SessionInfo getSessionInfo(
            String sessionId,
            String subscriptionId,
            String destination,
            Role role,
            Boolean isLocked
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.STOMP);

        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);

        SecurityUser user = new SecurityUser(
                UUID.randomUUID(),
                "test@email.com",
                "",
                role,
                isLocked
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        accessor.setUser(authentication);

        Message<byte[]> message =  MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        return new SessionInfo(message);
    }

}
