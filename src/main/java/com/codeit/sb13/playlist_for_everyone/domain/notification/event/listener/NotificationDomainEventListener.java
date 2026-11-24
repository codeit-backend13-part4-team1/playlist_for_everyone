package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationPublishBatch;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationSource;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationAsyncProcessor;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationSubmitter;
import com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring.NotificationMetrics;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationTargetReader;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 업무 이벤트를 받아 수신자를 조회하고 알림 요청을 구성한다.
 *
 * <p>트랜잭션 안에서 발행된 이벤트는 커밋 후 처리한다. 트랜잭션 밖에서 발행된 이벤트 중에는
 * {@link WatchingSessionStartedEvent}만 {@code fallbackExecution = true}에 따라 발행 스레드에서 즉시 처리한다.
 *
 * <p>수신자 조회와 요청 구성은 리스너를 실행하는 스레드에서 수행한다. 요청이 있으면 목록 전체를
 * {@link NotificationAsyncProcessor}에 작업 하나로 제출한다. 프로세서는 작업 스레드에서 요청을 순차 발행하고,
 * {@link NotificationEventListener}가 이를 받아 저장을 시작한다.
 *
 * <p>조회·생성·제출 실패는 여기서, 개별 재발행 실패는 프로세서에서 처리한다.
 * 업무 식별자와 예외 타입만 기록하며 이름·제목·본문·원본 예외 메시지는 기록하지 않는다.
 * 수신자 중복 제거 범위는 한 이벤트의 수신자 목록이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDomainEventListener {

    private final NotificationTargetReader targetReader;
    private final NotificationAsyncProcessor processor;
    private final NotificationMetrics metrics;
    private final NotificationSubmitter notificationSubmitter;

    /** 팔로우받은 사용자에게 INFO 요청을 만든다. 이름은 이벤트 스냅샷을 사용하고 공백이면 '사용자'로 보정한다. */
    @TransactionalEventListener
    public void handleUserFollowed(UserFollowedEvent event) {
        Map<String, UUID> ids = Map.of("followerId", event.followerId(), "followeeId", event.followeeId());
        String actionKey = "follow:%s:%s".formatted(event.followerId(), event.followeeId());
        try {
            NotificationCreateRequestedEvent request = new NotificationCreateRequestedEvent(event.followeeId(), "새로운 팔로워",
                    "%s님이 회원님을 팔로우했습니다.".formatted(resolveUserName(event.followerName())),
                    NotificationLevel.INFO, NotificationSource.USER_FOLLOWED);
            submit("팔로우", ids, List.of(request), batch -> notificationSubmitter.submit(actionKey, batch));
        } catch (RuntimeException failure) {
            logCreationFailure("팔로우", ids, failure);
        }
    }

    /** 변경 전·후 권한과 조사를 반영하여 변경 대상 사용자에게 요청한다. 자기 알림 제외 규칙은 적용하지 않는다. */
    @TransactionalEventListener
    public void handleUserRoleChanged(UserRoleChangedEvent event) {
        Map<String, UUID> ids = Map.of("userId", event.userId());
        try {
            String postposition = switch (event.newRole()) {
                case ADMIN -> "으로";
                case USER -> "로";
            };
            NotificationCreateRequestedEvent request = new NotificationCreateRequestedEvent(event.userId(), "권한 변경",
                    "회원님의 권한이 %s에서 %s%s 변경되었습니다.".formatted(event.previousRole(), event.newRole(), postposition),
                    NotificationLevel.INFO, NotificationSource.USER_ROLE_CHANGED);
            submit("권한 변경", ids, List.of(request));
        } catch (RuntimeException failure) {
            logCreationFailure("권한 변경", ids, failure);
        }
    }

    /** 구독자 이름과 제목 스냅샷으로 소유자에게 요청하며, 자기 구독은 제출하지 않는다. */
    @TransactionalEventListener
    public void handlePlaylistSubscribed(PlaylistSubscribedEvent event) {
        if (event.ownerId().equals(event.subscriberId())) {
            return;
        }
        String actionKey = "playlist-subscribe:%s:%s".formatted(event.subscriberId(), event.playlistId());
        Map<String, UUID> ids = Map.of(
                "playlistId", event.playlistId(),
                "ownerId", event.ownerId(),
                "subscriberId", event.subscriberId());
        try {
            NotificationCreateRequestedEvent request = new NotificationCreateRequestedEvent(event.ownerId(), "새로운 구독자",
                    "%s님이 \"%s\" 플레이리스트를 구독했습니다.".formatted(resolveUserName(event.subscriberName()), event.playlistTitle()),
                    NotificationLevel.INFO, NotificationSource.PLAYLIST_SUBSCRIBED);
            submit("플레이리스트 구독", ids, List.of(request), batch -> notificationSubmitter.submit(actionKey, batch));
        } catch (RuntimeException failure) {
            logCreationFailure("플레이리스트 구독", ids, failure);
        }
    }

    /**
     * 커밋 후 처리 시점의 팔로워를 조회하고 중복 ID와 소유자를 제외하여 요청 목록을 만든다.
     * 조회는 현재 호출 스레드에서 실행한다. 조회 실패 또는 남은 수신자가 없으면 제출하지 않는다.
     */
    @TransactionalEventListener
    public void handlePlaylistCreated(PlaylistCreatedEvent event) {
        Map<String, UUID> ids = Map.of("playlistId", event.playlistId(), "ownerId", event.ownerId());
        try {
            String ownerName = resolveUserName(event.ownerName());
            List<NotificationCreateRequestedEvent> requests =
                    getFollowerIdsOrEmpty(event.ownerId(), (exceptionType) ->
                            log.warn("플레이리스트 생성 알림 수신자 조회실패. playlistId={}, ownerId={}, exceptionType={}",
                                    event.playlistId(), event.ownerId(), exceptionType))
                    .stream()
                    .distinct()
                    .filter(receiverId -> !event.ownerId().equals(receiverId))
                    .map(receiverId -> new NotificationCreateRequestedEvent(receiverId, "팔로우한 사용자의 새 플레이리스트",
                            "%s님이 \"%s\" 플레이리스트를 만들었습니다.".formatted(ownerName, event.playlistTitle()),
                            NotificationLevel.INFO, NotificationSource.PLAYLIST_CREATED))
                    .toList();
            submit("플레이리스트 생성", ids, requests);
        } catch (RuntimeException failure) {
            logCreationFailure("플레이리스트 생성", ids, failure);
        }
    }

    /** 커밋 후 구독자를 조회하고 중복 ID·행위자를 제외한다. 제목은 이벤트 스냅샷을 사용한다. */
    @TransactionalEventListener
    public void handlePlaylistContentAdded(PlaylistContentAddedEvent event) {
        Map<String, UUID> ids = Map.of(
                "playlistId", event.playlistId(),
                "contentId", event.contentId(),
                "actorId", event.actorId());
        String actionKey = "playlist-content-add:%s:%s".formatted(event.playlistId(), event.contentId());
        try {
            List<NotificationCreateRequestedEvent> requests = getSubscriberIdsOrEmpty(event).stream()
                    .distinct()
                    .filter(receiverId -> !event.actorId().equals(receiverId))
                    .map(receiverId -> new NotificationCreateRequestedEvent(receiverId, "구독한 플레이리스트에 새 콘텐츠",
                            "\"%s\" 플레이리스트에 \"%s\" 콘텐츠가 추가되었습니다.".formatted(event.playlistTitle(), event.contentTitle()),
                            NotificationLevel.INFO, NotificationSource.PLAYLIST_CONTENT_ADDED))
                    .toList();
            submit("콘텐츠 추가", ids, requests, batch -> notificationSubmitter.submit(actionKey, batch));
        } catch (RuntimeException failure) {
            logCreationFailure("콘텐츠 추가", ids, failure);
        }
    }

    /**
     * DM 수신자에게 이름 스냅샷으로 요청한다. 같은 발신자·수신자는 제외한다.
     * 접속 여부나 WebSocket 구독 여부는 검사하지 않으며 메시지 본문도 사용하지 않는다.
     */
    @TransactionalEventListener
    public void handleDirectMessageCreated(DirectMessageCreatedEvent event) {
        if (event.senderId().equals(event.receiverId())) {
            return;
        }
        Map<String, UUID> ids = Map.of(
                "directMessageId", event.directMessageId(),
                "conversationId", event.conversationId(),
                "receiverId", event.receiverId());
        try {
            NotificationCreateRequestedEvent request = new NotificationCreateRequestedEvent(event.receiverId(), "새로운 메시지",
                    "%s님이 메시지를 보냈습니다.".formatted(resolveUserName(event.senderName())),
                    NotificationLevel.INFO, NotificationSource.DIRECT_MESSAGE_CREATED);
            submit("DM", ids, List.of(request));
        } catch (RuntimeException failure) {
            logCreationFailure("DM", ids, failure);
        }
    }

    /**
     * 시청자의 팔로워 중 본인을 제외하고 중복 ID를 제거해 {@link NotificationLevel#INFO} 알림 요청을 구성한다.
     * 이벤트의 시청자 이름에 표시할 텍스트가 없으면 '사용자'를 사용한다.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void handleWatchingSessionStarted(WatchingSessionStartedEvent event) {
        Map<String, UUID> ids = Map.of(
                "contentId", event.contentId(),
                "watcherId", event.watcherId());
        String watcherName = resolveUserName(event.watcherName());
        try {
            List<NotificationCreateRequestedEvent> requests = getFollowerIdsOrEmpty(event.watcherId(),
                    (exceptionType) -> log.warn("콘텐츠 시청 시작 알림 수신자 조회실패. contentId={}, watcherId={}, exceptionType={}",
                            event.contentId(), event.watcherId(), exceptionType))
                    .stream()
                    .distinct()
                    .filter(receiverId -> !event.watcherId().equals(receiverId))
                    .map(receiverId -> new NotificationCreateRequestedEvent(
                            receiverId, "팔로우한 사용자의 같이 보기 참여", "%s님이 \"%s\" 같이 보기에 참여했습니다."
                            .formatted(watcherName, event.contentTitle()),
                            NotificationLevel.INFO, NotificationSource.WATCHING_SESSION_STARTED))
                    .toList();
            submit("콘텐츠 시청 시작", ids, requests);
        }catch (RuntimeException failure) {
            logCreationFailure("콘텐츠 시청 시작", ids, failure);
        }
    }


    /** @Async 프록시에 목록 전체를 한 번 제출한다. 거부 시 호출 스레드에서 대신 실행하거나 재시도하지 않는다. */
    private void submit(String source, Map<String, UUID> ids, List<NotificationCreateRequestedEvent> requests) {
        submit(source, ids, requests, processor::publish);
    }

    private void submit(String source, Map<String, UUID> ids, List<NotificationCreateRequestedEvent> requests, Consumer<NotificationPublishBatch> publishBatch){
        if (requests.isEmpty()) {
            return;
        }
        NotificationPublishBatch batch = new NotificationPublishBatch(source, ids, requests);
        try {
            publishBatch.accept(batch);
        } catch (TaskRejectedException failure) {
            log.warn("{} 알림 작업 제출 거부. sourceIds={}, requestCount={}, exceptionType={}",
                    source, ids, requests.size(), failure.getClass().getSimpleName());
        }
    }

    private void logCreationFailure(String source, Map<String, UUID> ids, RuntimeException failure) {
        log.warn("{} 알림 요청 생성 실패. sourceIds={}, exceptionType={}", source, ids, failure.getClass().getSimpleName());
    }

    /** 정상 조회와 조회 실패를 로그로 구분하되, 실패한 조회에서는 알림을 제출하지 않는다. */
    private List<UUID> getFollowerIdsOrEmpty(UUID ownerId, Consumer<String> log) {
        try {
            return targetReader.findFollowerIds(ownerId);
        } catch (RuntimeException failure) {
            metrics.recordLookupFailure();
            log.accept(failure.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<UUID> getSubscriberIdsOrEmpty(PlaylistContentAddedEvent event) {
        try {
            return targetReader.findSubscriberIds(event.playlistId());
        } catch (RuntimeException failure) {
            metrics.recordLookupFailure();
            log.warn("콘텐츠 추가 알림 수신자 조회 실패. playlistId={}, contentId={}, actorId={}, exceptionType={}",
                    event.playlistId(), event.contentId(), event.actorId(), failure.getClass().getSimpleName());
            return List.of();
        }
    }

    /** null·빈 문자열·공백뿐인 이름은 '사용자'로 대체하고, 그 외에는 앞뒤 공백도 그대로 유지한다. */
    private String resolveUserName(String name) {
        return StringUtils.hasText(name) ? name : "사용자";
    }
}
