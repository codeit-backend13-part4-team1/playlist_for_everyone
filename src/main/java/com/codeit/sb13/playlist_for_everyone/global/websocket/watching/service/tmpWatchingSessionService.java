package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMapper;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.*;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;


@Slf4j
@Component
@RequiredArgsConstructor
public class tmpWatchingSessionService {
    private final WebSocketSessionRegistry sessionRegistry;

    private final ViewingSessionService viewingSessionService;
    private final ChattingSessionService chattingSessionService;
    private final UserService userService;
    private final ContentService contentService;

    private final WatchingSessionMapper mapper;


    private final MatchRule UUID_PATTERN = MatchRule
            .builder()
            .pattern(UUID_REGEX)
            .build();

    private final MatchRule VIEW_DESTINATION_PATTERN =
            MatchRule.builder()
                    .prefix(PATH_PREFIX)
                    .pattern(UUID_REGEX)
                    .postfix(VIEW_POSTFIX)
                    .build();

    private final MatchRule CHAT_DESTINATION_PATTERN =
            MatchRule.builder()
                    .prefix(PATH_PREFIX)
                    .pattern(UUID_REGEX)
                    .postfix(CHAT_POSTFIX)
                    .build();

    /*
     * ViewingSession 용 매서드
     */
    // todo - join 과 leave 의 동시실행 문제 존재. -> 서비스 단 매서드에서 처리.
    //  및 redis 상태 정리 문제 -> 하단 서비스 매서드에서 처리

    /**
     * View 세션에 사용자를 추가한다.
     * @param sessionId 세션 ID
     * @param subscriptionId 구독 ID
     * @param destination 구독 경로
     * @param auth 로그인 된 사용자 정보.
     */
    public void joinViewerSession(String sessionId, String subscriptionId, String destination, SecurityUser auth){
        // 0. 리소스 검증
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(auth);

        UUID contentId = getContentId(destination);
        WSSession session = sessionRegistry.query(sessionId).orElseThrow(WebSocketSessionNotFoundException::new);
        ContentDto content = contentService.getById(contentId);
        WSSubscription subscription;

        // 세션에 대해 구독 작업이 직렬화 되도록 sync
        synchronized (session) {
            // 1. 구독 등록
            subscription = session.subscribe(subscriptionId, destination);
            try {
                // 2. 비즈니스 로직 등록
                viewingSessionService.join(sessionId, subscriptionId, contentId);
            } catch (RuntimeException e) {
                log.error("[{}] 구독 등록 실패 - contentId : {}, sessionId : {}, subscriptionId : {}",
                        this.getClass().getSimpleName(),
                        contentId,
                        sessionId,
                        subscriptionId,
                        e);
                try {
                    session.unsubscribe(subscription);
                } catch (RuntimeException rollbackException) {
                    log.error("JOIN 롤백 실패", rollbackException);
                    e.addSuppressed(rollbackException);
                }

                throw e;
            }
        }


        // 3. 메세지 전송
        sendViewingSessionMessage(subscription, ChangeType.JOIN, auth, content);
    }

    /**
     * unsubscribe 프레임을 수신했을 때 호출되는 메서드.
     * 세션, 구독 id 만 있는 세션 정보를 받아서, 경로를 복원하고 경로에 구독된
     * @param sessionId 세션 id
     * @param subscriptionId 구독 id
     * @param auth 인증 정보
     */
    public void leaveViewerSession(String sessionId, String subscriptionId, SecurityUser auth){
        // 0. 리소스 검증
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(auth);

        WSSession session = sessionRegistry.query(sessionId)
                .orElseThrow(WebSocketSessionNotFoundException::new);
        WSSubscription subscription;
        ContentDto content;

        synchronized (session) {
            subscription = session.getSubscription(subscriptionId)
                    .orElseThrow(WebSocketSessionNotFoundException::new);
            String destination = subscription.destination();
            UUID contentId = getContentId(destination);

            /**
             * 지금은 취소된 구독 개별로 연결을 종료할 수 없기 때문에, 세션에서 구독을 취소하는건 작용이 없는 매서드이다.
             * 비즈니스 로직에서 구독이 취소되는 로직은 실제 구독과 동기화 되는 게 아닌, send 메세지를 막는 역할만 한다.
             */
            // todo - 재시도 로직 작성이 필요하다.
            try {
                // 1. 구독 등록 취소
                viewingSessionService.leave(sessionId, subscriptionId, contentId);
            } catch (RuntimeException e) {
                log.error("[{}] 구독 취소 실패 - contentId : {}, sessionId : {}, subscriptionId : {}",
                        this.getClass().getSimpleName(),
                        contentId,
                        sessionId,
                        subscriptionId,
                        e);
                throw new WebSocketSessionRegisterException(e);
            } finally {
                // 2. 세션에 등록된 구독 자체는 무조건 취소.
                session.unsubscribe(subscription);
            }

            content = contentService.getById(contentId);
        }
        // 3. 메세지 전송
        sendViewingSessionMessage(subscription, ChangeType.LEAVE, auth, content);
    }

    // viewing 세션 초기 정보 전송 메세지.
    private void sendViewingSessionMessage(
            WSSubscription subscription,
            ChangeType type,
            SecurityUser auth,
            ContentDto content
    ){
        // 3. 메세지 전송 객체 조립
        // 메세지가 전송 실패한다고 해도, 구독이 해제되어선 안된다.
        // 지금은 별도 메서드에 발신이 실패해도 조용히 넘기는 것으로 우선 둔다.
        // todo - 추후 메세지 발신 재시도 로직 필요.
        WatchingSessionChange change = null;

        try {
            User user = userService.getById(auth.getUserId());

            Watcher watcher = mapper.toWatcher(user);
            WatchContent watchContent = mapper.toWatchContent(content);
            WatchingSessionDto dto = new WatchingSessionDto(
                    subscription.uuid(),
                    subscription.createdAt(),
                    watcher,
                    watchContent
            );
            change = new WatchingSessionChange(
                    type,
                    dto,
                    viewingSessionService.getCurrentViewerCount(content.id()));
        } catch (RuntimeException e) {
            log.warn("[{}] 페이로드 조립 실패 - contentId : {}, subscriptionId : {}",
                    this.getClass().getSimpleName(),
                    content.id(),
                    subscription.id(),
                    e);
        } finally {
            try {
                // 4. 초기 작성에 실패해도 메세지 발송.
                RedisMessageDto<WatchingSessionChange> payload =
                        new RedisMessageDto<>(subscription.destination(), change);
                viewingSessionService.send(content.id(), payload);
            } catch (RuntimeException e) {
                log.warn("[{}] 메세지 발송 실패 - contentId : {}, subscriptionId : {}",
                        this.getClass().getSimpleName(),
                        content.id(),
                        subscription.id(),
                        e);
            }
        }
    }




    /*
     * chatting 세션 전용 매서드
     */

    public void joinChattingSession(String sessionId, String subscriptionId, String destination, SecurityUser auth){
        // 0. 리소스 검증
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(auth);

        UUID contentId = getContentId(destination);
        WSSession session = sessionRegistry.query(sessionId).orElseThrow(WebSocketSessionNotFoundException::new);

        synchronized (session) {
            WSSubscription subscription = session.subscribe(subscriptionId, destination);
            try {
                chattingSessionService.join(sessionId, subscriptionId, contentId);
            } catch (RuntimeException e) {
                log.error("[{}] 구독 등록 실패 - contentId : {}, sessionId : {}, subscriptionId : {}",
                        this.getClass().getSimpleName(),
                        contentId,
                        sessionId,
                        subscriptionId,
                        e);
                try {
                    session.unsubscribe(subscription);
                } catch (RuntimeException rollbackException) {
                    log.error("[{}] 구독 롤백 작업 실패",
                            this.getClass().getSimpleName(),
                            rollbackException);
                    e.addSuppressed(rollbackException);
                }
                throw e;
            }
        }
    }

    public void leaveChattingSession(String sessionId, String subscriptionId, SecurityUser auth) {
        // 0. 리소스 검증
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(auth);

        WSSession session = sessionRegistry.query(sessionId)
                .orElseThrow(WebSocketSessionNotFoundException::new);

        synchronized (session) {
            WSSubscription subscription = session.getSubscription(subscriptionId)
                    .orElseThrow(WebSocketSessionNotFoundException::new);
            String destination = subscription.destination();
            UUID contentId = getContentId(destination);

            /**
             * 지금은 취소된 구독 개별로 연결을 종료할 수 없기 때문에, 세션에서 구독을 취소하는건 작용이 없는 매서드이다.
             * 비즈니스 로직에서 구독이 취소되는 로직은 실제 구독과 동기화 되는 게 아닌, send 메세지를 막는 역할만 한다.
             */
            // todo - 재시도 로직 작성이 필요하다.
            try {
                chattingSessionService.leave(sessionId, subscriptionId, contentId);
            } catch (RuntimeException e) {
                log.error("[{}] 구독 등록 실패 - contentId : {}, sessionId : {}, subscriptionId : {}",
                        this.getClass().getSimpleName(),
                        contentId,
                        sessionId,
                        subscriptionId,
                        e);
                throw new WebSocketSessionRegisterException(e);
            } finally {
                session.unsubscribe(subscription);
            }

        }
    }


    /*
     * 리소스 정리 매서드
     */

    private record SubscriptionDto(
            String sessionId,
            String subscriptionId,
            UUID contentId
    ){
        SubscriptionDto {
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(subscriptionId);
            Objects.requireNonNull(contentId);
        }
    }

    public void disconnectSessionWithCleanUp(String sessionId) {
        WSSession session = getSessionOrExcept(sessionId);

        synchronized (session) {
            List<SubscriptionDto> viewingSessionList = new ArrayList<>();
            List<SubscriptionDto> chattingSessionList = new ArrayList<>();

            // 대상 정보 확인.
            session.getAllSubscriptions().forEach(
                    subscription -> {
                        String subscriptionId = subscription.id();
                        String destination = subscription.destination();
                        UUID contentId = getContentId(destination);
                        if (VIEW_DESTINATION_PATTERN.check(destination)) {
                            // 뷰잉 세션 서비스 관련
                            viewingSessionList.add(new SubscriptionDto(sessionId, subscriptionId, contentId));
                        }

                        if (CHAT_DESTINATION_PATTERN.check(destination)) {
                            // 채팅 세션 서비스 관련
                            chattingSessionList.add(new SubscriptionDto(sessionId, subscriptionId, contentId));
                        }

                    }
            );

        }

    }


    /*
     * 공용 편의 매서드
     */

    private WSSession getSessionOrExcept(String SessionId){
        return sessionRegistry.query(SessionId).orElseThrow(WebSocketSessionNotFoundException::new);
    }


    private UUID getContentId(String destination){
        try{
            List<String> extract =
                    UUID_PATTERN.extract(destination);

            if (extract.isEmpty()) {
                log.error("[{}] (dst:{}) - 컨텐츠 ID 추출 실패. - pattern:{}",
                        this.getClass().getSimpleName(),
                        destination,
                        UUID_PATTERN
                );
                throw new NullPointerException();
            }

            return UUID.fromString(extract.get(0));
        } catch (NullPointerException e) {
            throw new WebSocketDestinationInvalidException(e);
        }
    }

}
