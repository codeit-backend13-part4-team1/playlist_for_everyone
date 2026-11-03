package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.ConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role.SessionRefreshRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 소켓 으로부터의 메세지를 처리하는 인터셉터.
 * 실제 컨트롤러나, 관련 레지스터로 가는 메세지를 직접 조작하거나,
 * 비즈니스 로직의 성공, 실패가 연결에 영향을 끼쳐야 하는 경우에 사용된다.
 * 다음과 같은 작업들이다.
 * - 메세지에 인증 정보 세팅
 * 메세지의 정보를 바탕으로, 비즈니스 로직을 조작하는경우,
 * Controller 와 WebSocketEventListener 을 사용해도 된다.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketInterceptor implements ChannelInterceptor {

    private final HeartBeatDelay delay = HeartBeatDelay.get(10000L);    // 설정에 하트비트값 추가하면 해당값 입력.
    private final SessionMatcher heartBeatMatcher;

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final ConnectionRegistry connectionRegistry;

    // 서버가 올라가기 전, matcher 에 Rule 을 적재하는 구간.
    @PostConstruct
    private void setup(){
        heartBeatMatcher.add(new SessionRefreshRule());
    }

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        /*
         * 하트비트 프레임의 경우, getType() = SimpType.HEARTBEAT
         * SessionID 와 Principal(getUser() 가 null 이 아니라면)
         * 가 존재하는 프레임으로, 인터셉터에 들어온다고 한다.
         * + 일반 프레임이 도달하면, heartbeat 가 아니더라도 연결된 것으로 판단함.
         * -> SessionID 가 존재하는 프레임은 heartbeat 로 판단해도 된다.
         * -> 다만 딜레이를 추가해서 로직을 너무 자주 돌리는 것을 방지한다.
         */
        heartbeat(message,channel);

        // java 17 버전 NPE 방지.
        if (accessor.getCommand() == null) return message;

        return switch (accessor.getCommand()) {
            case CONNECT -> connect(accessor,message);
            case SUBSCRIBE -> subscribe(accessor,message);
            case UNSUBSCRIBE -> unsubscribe(accessor,message);
            case DISCONNECT -> disconnect(accessor,message);
            case SEND -> send(accessor,message);
            default -> message;
        };
    }

    /*
     * 모든 커맨드 프레임 에 대한 Message 조작 로직.
     */

    /**
     * 웹 소켓의 heartbeat 프레임 에 해당하는 로직.
     * sessionId 가 있는 프레임은 전부 해당 세션에 대한 heartbeat 으로 간주한다.
     * @param message .
     * @param channel .
     */
    private void heartbeat(Message<?> message, MessageChannel channel) {
        SessionInfo info = new SessionInfo(message);
        // 세션 헤더가 없다면, 로직 패스.
        if (info.session() == null) return;
        // 세션 id 가 같으면, 같은 문자열 -> 같은 락 을 가지게 된다.
        if (!delay.check(info.session())) return;
        // 딜레이 체크 이후 작업.
        heartBeatMatcher.run(info);

    }

    /*
     * 프레임의 커멘드 별 Message 를 직접 조작해야 할 때 사용되는 컨테이너 메서드.
     */

    private Message<?> send(StompHeaderAccessor accessor, Message<?> message) {
        // todo - send 경로 화이트 리스트 필요?
        return message;
    }

    private Message<?> connect(StompHeaderAccessor accessor, Message<?> message) {
        authenticate(accessor);
        connectionRegistry.register(new SessionInfo(message).toData());
        return message;
    }

    private Message<?> subscribe(StompHeaderAccessor accessor, Message<?> message) {
        connectionRegistry.register(new SessionInfo(message).toData());
        return message;
    }

    private Message<?> unsubscribe(StompHeaderAccessor accessor,Message<?> message){
        connectionRegistry.unregister(new SessionInfo(message).toData());
        return message;
    }

    private Message<?> disconnect(StompHeaderAccessor accessor,Message<?> message){
        connectionRegistry.unregister(new SessionInfo(message).toData());
        return message;
    }

    /*
    커넥션 관련 로직 편의 매서드
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String token = token(accessor);

        UUID userId = jwtTokenProvider.parseAccessToken(token);
        User user = userService.getById(userId);

        if (user.isLocked()) throw new LockedException("잠긴 계정입니다.");

        SecurityUser securityUser = new SecurityUser(
                user.getId(),
                user.getEmail(),
                "",
                user.getRole(),
                user.isLocked()
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                securityUser,
                null,
                securityUser.getAuthorities()
        );

        accessor.setUser(authentication);
    }

    private String token(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");

        if (header == null || !header.startsWith("Bearer "))
            throw new BadCredentialsException("Bearer 토큰이 비어 있습니다.");

        return header.substring(7);
    }
}
