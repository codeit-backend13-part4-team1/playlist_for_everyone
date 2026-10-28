package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistryDelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketInterceptor implements ChannelInterceptor {

    private final WatcherRegistry watcherRegistry;
    private final WatcherRegistryDelay delay;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (accessor.getSessionId() != null) sessionRefresh(accessor);

        // java 17 버전 NPE 방지.
        if (accessor.getCommand() == null) return message;

        return switch (accessor.getCommand()) {
            case CONNECT -> connect(accessor,message);
            case SUBSCRIBE -> subscribe(accessor,message);
            case SEND -> send(accessor,message);
            default -> message;
        };
    }

    // STOMP 의 모든 하트비트인지 미정?
    private void sessionRefresh(StompHeaderAccessor accessor) {
        String session = accessor.getSessionId();

        // 세션 별 리프레시 시도 딜레이 작성
        if (!delay.checkRefresh(session)) return;

        try {
            watcherRegistry.refresh(session);
        } catch (RuntimeException e) {
            log.error("[{}] 세션 갱신 실패 - sessionId : {}", "ChannelInterceptor", session, e);
        }
    }

    private Message<?> send(StompHeaderAccessor accessor, Message<?> message) {
        // /sub/... 에 해당하는 destination 은 메세지를 통과시키지 않음.

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

    /*
    커넥션 관련 로직 편의 매서드
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String token = token(accessor);

        // todo - token 으로 유저 정보를 조회하는 로직 -> princepal 반환 후 user 로 세팅.

        accessor.setUser(null);
    }

    private String token(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");

        // todo - 인증관련 예외가 생기면, 예외 대체
        if (header == null || !header.startsWith("Bearer "))
            throw new RuntimeException("토큰이 없습니다.");

        return header.substring(7);
    }

    /*
    구독 관련 로직과 편의 매서드
     */

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
