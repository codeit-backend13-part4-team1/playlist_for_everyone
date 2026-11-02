package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WatcherRegistryDelay;
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

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 소켓 으로부터의 메세지를 처리하는 인터셉터.
 * 실제 컨트롤러나, 관련 레지스터로 가는 메세지를 직접 조작하거나,
 * 비즈니스 로직의 성공, 실패가 연결에 영향을 끼쳐야 하는 경우에 사용된다.
 * 다음과 같은 작업들이다.
 * - 메세지에 인증 정보 세팅
 *
 * 메세지의 정보를 바탕으로, 비즈니스 로직을 조작하는경우,
 * Controller 와 WebSocketEventListener 을 사용해도 된다.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketInterceptor implements ChannelInterceptor {

    private final WatcherRegistry watcherRegistry;
    private final WatcherRegistryDelay delay;

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (accessor.getSessionId() != null) all(message,channel);

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

    // STOMP 의 모든 하트비트인지 미정?
    private void all(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String session = accessor.getSessionId();

        // 세션 별 리프레시 시도 딜레이 작성
        if (!delay.checkRefresh(session)) return;

        try {
            watcherRegistry.refresh(session);
            delay.updateRefreshTime(session);
        } catch (RuntimeException e) {
            log.error("[{}] 세션 갱신 실패 - sessionId : {}", "ChannelInterceptor", session, e);
        }
    }

    /*
     * 프레임의 커멘드 별 Message 를 직접 조작해야 할 때 사용되는 컨테이너 메서드.
     */

    private Message<?> send(StompHeaderAccessor accessor, Message<?> message) {
        return message;
    }

    private Message<?> connect(StompHeaderAccessor accessor, Message<?> message) {
        // connection 관련 로직
        authenticate(accessor);

        return message;
    }

    private Message<?> subscribe(StompHeaderAccessor accessor, Message<?> message) {
        // subscription 관련 로직.
        registerContentId(accessor);

        return message;
    }

    private Message<?> unsubscribe(StompHeaderAccessor accessor,Message<?> message){
        return message;
    }

    private Message<?> disconnect(StompHeaderAccessor accessor,Message<?> message){
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

    /*
    구독 관련 로직과 편의 매서드
     */


    // todo - 웹 소켓 연결과 별도로 연결 세션 구독에 대해선 이벤트 기반으로 옮긴다?
    // 컨텐츠 실시간 시청으로 가는 요청에서 컨텐츠 id 를 파싱, 등록한다.
    private void registerContentId(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (destination != null && !destination.trim().isEmpty()){
            contentsIdParse(destination)
                    .ifPresent(contentId ->
                            watcherRegistry.register(
                                    accessor.getSessionId(),
                                    accessor.getSubscriptionId(),
                                    contentId));
        }
    }

    private Optional<String> contentsIdParse(String url){
        String uuidPattern = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";

        Pattern pattern = Pattern.compile("^/sub/contents/" + uuidPattern + "/watch$");

        Matcher matcher = pattern.matcher(url);

        if (matcher.matches())
            return Optional.ofNullable(matcher.group(1));
        else
            return Optional.empty();
    }
}
