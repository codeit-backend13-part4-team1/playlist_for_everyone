package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMapper;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.*;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionStartedEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;


@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionServiceImpl implements WatchingSessionService {
    private final WebSocketSessionRegistry sessionRegistry;

    private final ViewingSessionService viewingSessionService;
    private final ChattingSessionService chattingSessionService;
    private final UserService userService;
    private final ContentService contentService;

    private final WatchingSessionMapper mapper;

    private final ApplicationEventPublisher eventPublisher;

    private final Locks locks;

    private final MatchRule UUID_PATTERN = MatchRule
            .builder()
            .pattern(UUID_REGEX)
            .build();

    private final MatchRule VIEW_DESTINATION_PATTERN =
            MatchRule.builder()
                    .pattern(PATH_PREFIX + UUID_REGEX + VIEW_POSTFIX)
                    .build();

    private final MatchRule CHAT_DESTINATION_PATTERN =
            MatchRule.builder()
                    .pattern(PATH_PREFIX + UUID_REGEX + CHAT_POSTFIX)
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
    @Override
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
                viewingSessionService.join(subscription, contentId, auth.getUserId());
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

        // 3. 메세지 전송을 위한 리소스 조회 및 메세지 전송
        try{
            User user = userService.getById(auth.getUserId());
            sendViewingSessionMessage(subscription, ChangeType.JOIN, user, content);
            // 4. 알림 발송
            eventPublisher.publishEvent(new WatchingSessionStartedEvent(
                    user.getId(),
                    user.getName(),
                    content.id(),
                    content.title()
            ));
        } catch (RuntimeException e) {
            log.warn("[{}] 시청 세션 메세지 발신 실패, user: {}, session : {}, contentId : {}",
                    this.getClass().getSimpleName(), auth.getUserId(), subscription.id(), content.id());
        }
    }

    /**
     * unsubscribe 프레임을 수신했을 때 호출되는 메서드.
     * 세션, 구독 id 만 있는 세션 정보를 받아서, 경로를 복원하고 경로에 구독된
     * @param sessionId 세션 id
     * @param subscriptionId 구독 id
     * @param auth 인증 정보
     */
    @Override
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
            try {
                // 1. 구독 등록 취소
                viewingSessionService.leave(subscription, contentId);
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

        try {
            // 3. 메세지 전송을 위한 리소스 조회 및 메세지 전송
            User user = userService.getById(auth.getUserId());
            sendViewingSessionMessage(subscription, ChangeType.LEAVE, user, content);
        } catch (RuntimeException e) {
            log.warn("[{}] 시청 세션 메세지 발신 실패, user: {}, session : {}, contentId : {}",
                    this.getClass().getSimpleName(), auth.getUserId(), subscription.id(), content.id());
        }
    }

    // viewing 세션 초기 정보 전송 메세지 조립용 매서드
    private void sendViewingSessionMessage(
            WSSubscription subscription,
            ChangeType type,
            User user,
            ContentDto content
    ){
        // 3. 메세지 전송 객체 조립
        // 메세지가 전송 실패한다고 해도, 구독이 해제되어선 안된다.
        // 지금은 별도 메서드에 발신이 실패해도 조용히 넘기는 것으로 우선 둔다.
        WatchingSessionChange change = null;

        try {
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

    @Override
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
                chattingSessionService.join(subscription, contentId);
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

    @Override
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
            try {
                chattingSessionService.leave(subscription, contentId);
            } catch (RuntimeException e) {
                log.error("[{}] 구독 취소 실패 - contentId : {}, sessionId : {}, subscriptionId : {}",
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

    @Async("watchingSessionExecutor")
    @Override
    public void sendChatMessage(UUID contentId, UUID userId, String message) {
        Objects.requireNonNull(contentId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(message);

        User user = userService.getById(userId);

        // 실질적으로 UserSummary 와 같은 필드를 가진 객체.
        // 기능 검정 후 클래스 교체 및 테스트 코드 변경.
        Watcher tmp = mapper.toWatcher(user);

        UserSummary sender = new UserSummary(tmp.userId(), tmp.name(), tmp.profileImageUrl());

        chattingSessionService.send(contentId, new ContentChatDto(sender,message));
    }


    /*
     * 리소스 정리 매서드
     */

    /**
     * 세션 id 를 기준으로, 세션과 관련된 모든 리소스를 정리한다.
     * @param sessionId 세션 id
     */
    @Override
    public void cleanUpWithSessionId(String sessionId) {
        Objects.requireNonNull(sessionId);
        WSSession session = getSessionOrExcept(sessionId);

        synchronized (session) {
            // 병렬개 동시 처리를 위한 파라매터 리스트. session, subscription, content 순으로 가정한다.
            Map<UUID, List<WSSubscription>> viewingTargets = new HashMap<>();
            Map<UUID, List<WSSubscription>> chattingTargets = new HashMap<>();
            Set<UUID> contentIds = new HashSet<>();

            for (WSSubscription subscription : session.getAllSubscriptions()) {
                String destination = subscription.destination();

                boolean viewing = VIEW_DESTINATION_PATTERN.check(destination);
                boolean chatting = CHAT_DESTINATION_PATTERN.check(destination);

                if (!viewing && !chatting) {
                    continue;
                }

                try {
                    UUID contentId = getContentId(destination);

                    Map<UUID, List<WSSubscription>> target =
                            viewing ? viewingTargets : chattingTargets;

                    target.computeIfAbsent(
                            contentId,
                            id -> new ArrayList<>()
                    ).add(subscription);
                } catch (RuntimeException e) {
                    log.error("[{}] 구독 해지 리소스 작성 에러 - dst : {}",
                            this.getClass().getSimpleName(),
                            destination,
                            e);
                }
            }

            contentIds.addAll(viewingTargets.keySet());
            contentIds.addAll(chattingTargets.keySet());

            for (UUID contentId : contentIds) {
                // 동일 content lock 내에서  content 리소스 삭제 작업.
                synchronized (locks.lock(contentId)){
                    List<WSSubscription> viewingSubscriptionList = viewingTargets.get(contentId);
                    List<WSSubscription> chattingSubscriptionList = chattingTargets.get(contentId);

                    log.info("chatSessionList : {}", chattingSubscriptionList);

                    if (viewingSubscriptionList == null || !viewingSubscriptionList.isEmpty()){
                        viewingSessionService.leaveAll(viewingSubscriptionList, contentId);
                    }
                    if (chattingSubscriptionList == null || !chattingSubscriptionList.isEmpty()) {
                        chattingSessionService.leaveAll(chattingSubscriptionList, contentId);
                    }
                }
            }
        }
    }

    /**
     * 컨텐츠 id 와 관련된 모든 비즈니스 리소스를 정리한다.
     * @param contentId 컨텐츠 id
     */
    @Override
    public void cleanUpWithContentId(UUID contentId){
        Objects.requireNonNull(contentId);

        List<WSSubscription> unregisteredSubscriptionList = new ArrayList<>();

        // todo - 실제 세션 닫기는 구독 관리가 필요하다. 지금은 구독 관리를 simpBroker 가 함으로
        //  이 매서드에선 산순 구독 등록만 정리하고, 실제 구독 저일는 추후 다른 매서드에서 하도록 한다.
        // 1. 컨텐츠에 연결된 리소스 식별 및 정리
        synchronized (locks.lock(contentId)) {
            unregisteredSubscriptionList.addAll(viewingSessionService.destroy(contentId));
            unregisteredSubscriptionList.addAll(chattingSessionService.destroy(contentId));
        }

        // 2. 세션 리소스 정리
        for (WSSubscription subscription : unregisteredSubscriptionList) {
            try {
                WSSession session = getSessionOrExcept(subscription.sessionId());
                session.unsubscribe(subscription);
            } catch (RuntimeException e) {
                log.error("[{}] 구독 해제 실패 - sessionId : {}, subscriptionId : {}",
                        this.getClass().getSimpleName(),
                        subscription.sessionId(),
                        subscription.id(),
                        e);
            }
        }
    }

    @Override
    public void refresh(String sessionId){
        WSSession session = getSessionOrExcept(sessionId);
        List<WSSubscription> subscriptions = session.getAllSubscriptions();

        List<UUID> subscriptionKeys = subscriptions.stream()
                .filter(s -> VIEW_DESTINATION_PATTERN.check(s.destination()))
                .map(WSSubscription::uuid)
                .toList();

        viewingSessionService.refresh(subscriptionKeys);
    }

    /*
     * 공용 편의 매서드
     */

    private WSSession getSessionOrExcept(String sessionId){
        return sessionRegistry.query(sessionId).orElseThrow(WebSocketSessionNotFoundException::new);
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
