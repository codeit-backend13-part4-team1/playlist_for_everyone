package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationTargetReader;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 팔로우·권한 변경과 플레이리스트 구독·생성·콘텐츠 추가 이벤트를 공통 알림 생성 요청으로 변환한다.
 * 생산 도메인은 업무 사실을 전달하고, 이 리스너가 수신자와 알림 문구를 결정한다.
 *
 * <p>DB 업무 이벤트는 기본 AFTER_COMMIT, fallbackExecution=false로 수신한다.
 * 업무가 롤백되거나 트랜잭션 없이 이벤트를 발행하면 알림 생성 요청으로 변환하지 않는다.
 * 현재 @Async 없이 업무 커밋 처리 스레드에서 실행하며, 여러 수신자도 순차적으로 처리한다.
 *
 * <p>변환을 위한 별도 트랜잭션은 열지 않는다. 커밋 후 재발행한 요청은
 * fallbackExecution=true인 NotificationEventListener가 받아 저장 서비스에 전달한다.
 * 수신자별 알림 저장은 NotificationService.save의 REQUIRES_NEW 트랜잭션에서 수행하고,
 * 저장 실패는 공통 요청 리스너가 처리한다. 알림 커밋 후 NotificationSseEventListener가
 * SSE 발행을 요청하며, 이 과정의 완료가 브라우저 수신 완료를 의미하지는 않는다.
 *
 * <p>플레이리스트 생성·콘텐츠 추가의 수신자 중복 제거는 한 번의 이벤트 처리에서 조회한 목록에 적용한다.
 * 동일한 업무 이벤트가 다시 발행되는 경우까지 중복을 방지하는 계약은 아니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDomainEventListener {

    private final ApplicationEventPublisher eventPublisher;

    private final NotificationTargetReader targetReader;

    /**
     * 팔로우받은 사용자(followeeId)에게 INFO 수준의 알림 생성을 요청한다.
     *
     * <p>문구에는 사용자를 다시 조회하지 않고 이벤트 발생 당시의 followerName을 사용한다.
     * 이름이 없거나 공백이면 '사용자'로 표시한다.
     */
    @TransactionalEventListener
    public void handleUserFollowed(UserFollowedEvent event){
        try {
            String followerName = resolveUserName(event.followerName());
            NotificationCreateRequestedEvent requestedEvent = new NotificationCreateRequestedEvent(
                    event.followeeId(),
                    "새로운 팔로워",
                    "%s님이 회원님을 팔로우했습니다.".formatted(followerName),
                    NotificationLevel.INFO);
            eventPublisher.publishEvent(requestedEvent);
        } catch (RuntimeException e){
            // 이미 커밋된 팔로우와 알림 처리를 분리하기 위해 변환·재발행 실패를 기록하고 종료한다.
            // 사용자 이름이나 원본 예외 메시지 대신 업무 식별자와 예외 타입을 남긴다.
            log.warn(
                    "팔로우 알림 변환·재발행 실패. followerId={}, followeeId={}, exceptionType={}",
                    event.followerId(),
                    event.followeeId(),
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 권한이 변경된 사용자(userId)에게 INFO 수준의 알림 생성을 요청한다.
     *
     * <p>사용자를 다시 조회하지 않고 이벤트에 담긴 변경 전·후 권한을 사용한다.
     * 변경된 권한에 따라 'ADMIN으로', 'USER로' 조사를 구분하며, 자기 알림 제외 규칙은 적용하지 않는다.
     *
     * <p>변환·재발행 중 RuntimeException은 사용자 식별자와 예외 타입만 기록하고 전파하지 않는다.
     */
    @TransactionalEventListener
    public void handleUserRoleChanged(UserRoleChangedEvent event){
        try {
            String postposition = switch (event.newRole()){
                case ADMIN -> "으로";
                case USER -> "로";
            };
            NotificationCreateRequestedEvent requestedEvent = new NotificationCreateRequestedEvent(
                    event.userId(), "권한 변경",
                    "회원님의 권한이 %s에서 %s%s 변경되었습니다.".formatted(event.previousRole(), event.newRole(), postposition),
                    NotificationLevel.INFO
                );

            eventPublisher.publishEvent(requestedEvent);
        } catch (RuntimeException e){
            log.warn(
                    "권한 변경 알림 변환·재발행 실패. userId={}, exceptionType={}",
                    event.userId(),
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 플레이리스트 소유자(ownerId)에게 INFO 수준의 알림 생성을 요청한다.
     * 소유자와 구독자가 같으면 자기 알림을 만들지 않는다.
     *
     * <p>문구에는 이벤트 발생 당시의 구독자 이름과 플레이리스트 제목을 사용한다.
     * 구독자 이름이 없거나 공백이면 '사용자'로 표시한다.
     *
     * <p>변환·재발행 중 RuntimeException은 업무 식별자와 예외 타입만 기록하고 전파하지 않는다.
     * 구독자 이름·플레이리스트 제목·원본 예외 메시지는 로그에 남기지 않는다.
     */
    @TransactionalEventListener
    public void handlePlaylistSubscribed(PlaylistSubscribedEvent event){
        try {
            if (event.ownerId().equals(event.subscriberId())){
                return;
            }
            String subscriberName = resolveUserName(event.subscriberName());
            NotificationCreateRequestedEvent requestedEvent = new NotificationCreateRequestedEvent(
                    event.ownerId(), "새로운 구독자",
                    "%s님이 \"%s\" 플레이리스트를 구독했습니다.".formatted(subscriberName, event.playlistTitle())
                    , NotificationLevel.INFO);

            eventPublisher.publishEvent(requestedEvent);
        } catch (RuntimeException e){
            log.warn(
                    """
                    플레이리스트 구독 알림 변환·재발행 실패.
                    playlistId={}, ownerId={}, subscriberId={}, exceptionType={}
                    """,
                    event.playlistId(),
                    event.ownerId(),
                    event.subscriberId(),
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 생성된 플레이리스트 소유자의 팔로워들에게 INFO 알림 생성을 요청한다.
     *
     * <p>팔로워는 이벤트 발생 당시의 목록을 전달받지 않고, 커밋 후 이 메서드가 처리하는 시점에 조회한다.
     * 조회 목록에서 중복 ID와 소유자를 제외하며, 남은 수신자가 없으면 요청을 발행하지 않는다.
     * 문구에는 이벤트의 ownerName·playlistTitle을 사용하고, 이름이 없거나 공백이면 '사용자'로 표시한다.
     *
     * <p>조회 헬퍼가 RuntimeException을 업무 식별자·예외 타입과 함께 기록하고 빈 목록을 반환한다.
     * 빈 목록이면 알림 요청 없이 종료하며, 정상 조회에서 팔로워가 없는 경우도 같은 흐름을 따른다.
     * 수신자별 변환·재발행 실패는 발행 헬퍼에서 처리하여 나머지 팔로워에게 계속 요청한다.
     */
    @TransactionalEventListener
    public void handlePlaylistCreated(PlaylistCreatedEvent event){
        List<UUID> followerIds = getFollowerIdsOrEmpty(event);
        if (followerIds.isEmpty()){
            return;
        }

        String ownerName = resolveUserName(event.ownerName());
        followerIds.stream()
                .distinct()
                .filter(receiverId -> !event.ownerId().equals(receiverId))
                .forEach(receiverId ->
                        publishPlaylistCreatedNotification(event, receiverId, ownerName));
    }

    /**
     * 콘텐츠가 추가된 플레이리스트의 구독자들에게 INFO 알림 생성을 요청한다.
     *
     * <p>구독자는 커밋 후 처리 시점에 조회하며, 중복 ID와 콘텐츠를 추가한 actorId를 제외한다.
     * 남은 수신자가 없으면 요청을 발행하지 않는다. 문구에는 이벤트의 playlistTitle·contentTitle을
     * 사용하므로 플레이리스트나 콘텐츠를 다시 조회하지 않는다.
     *
     * <p>조회 헬퍼가 RuntimeException을 업무 식별자·예외 타입과 함께 기록하고 빈 목록을 반환한다.
     * 빈 목록이면 스트림에서 알림을 발행하지 않으며, 정상 조회에서 구독자가 없는 경우도 동일하다.
     * 수신자별 변환·재발행 실패는 발행 헬퍼에서 처리하여 나머지 구독자에게 계속 요청한다.
     */
    @TransactionalEventListener
    public void handlePlaylistContentAdded(PlaylistContentAddedEvent event){
        getSubscriberIdsOrEmpty(event)
                .stream()
                .distinct()
                .filter(receiverId -> !event.actorId().equals(receiverId))
                .forEach(receiverId -> publishPlaylistContentAddedNotification(event, receiverId));
    }

    /**
     * 정상 조회 결과를 그대로 반환하고, RuntimeException 발생 시 로그를 남긴 뒤 빈 목록을 반환한다.
     * 관계가 없는 경우와 조회 실패 모두 발행 대상은 없지만, 실패 여부는 로그로 구분한다.
     */
    private List<UUID> getFollowerIdsOrEmpty(PlaylistCreatedEvent event) {
        try {
            return targetReader.findFollowerIds(event.ownerId());
        } catch (RuntimeException e){
            log.warn(
                    "플레이리스트 생성 알림 수신자 조회실패. playlistId={}, ownerId={}, exceptionType={}",
                    event.playlistId(),
                    event.ownerId(),
                    e.getClass().getSimpleName()
            );
            return List.of();
        }
    }

    /**
     * 보정한 소유자 이름과 생성 이벤트로 한 수신자의 공통 알림 요청을 만들어 재발행한다.
     * RuntimeException은 식별자와 예외 타입만 기록하여 다음 수신자의 처리가 이어지도록 한다.
     * 이름·제목·원본 예외 메시지는 이 실패 로그에 포함하지 않는다.
     */
    private void publishPlaylistCreatedNotification(PlaylistCreatedEvent event, UUID receiverId, String ownerName) {
        try {
            eventPublisher.publishEvent(new NotificationCreateRequestedEvent(
                    receiverId,
                    "팔로우한 사용자의 새 플레이리스트",
                    "%s님이 \"%s\" 플레이리스트를 만들었습니다.".formatted(ownerName, event.playlistTitle()),
                    NotificationLevel.INFO)
            );
        } catch (RuntimeException e){
            log.warn(
                    "플레이리스트 생성 알림 변환·재발행 실패. playlistId={}, ownerId={}, receiverId={}, exceptionType={}",
                    event.playlistId(),
                    event.ownerId(),
                    receiverId,
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 정상 조회 결과를 그대로 반환하고, RuntimeException 발생 시 로그를 남긴 뒤 빈 목록을 반환한다.
     * 관계가 없는 경우와 조회 실패 모두 발행 대상은 없지만, 실패 여부는 로그로 구분한다.
     */
    private List<UUID> getSubscriberIdsOrEmpty(PlaylistContentAddedEvent event) {
        try {
            return targetReader.findSubscriberIds(event.playlistId());
        } catch (RuntimeException e) {
            log.warn(
                    "콘텐츠 추가 알림 수신자 조회 실패. playlistId={}, contentId={}, actorId={}, exceptionType={}",
                    event.playlistId(),
                    event.contentId(),
                    event.actorId(),
                    e.getClass().getSimpleName()
            );
            return List.of();
        }
    }

    /**
     * 콘텐츠 추가 이벤트를 한 수신자의 공통 알림 요청으로 변환하여 재발행한다.
     * RuntimeException은 식별자와 예외 타입만 기록하여 다음 수신자의 처리가 이어지도록 한다.
     * 플레이리스트·콘텐츠 제목과 원본 예외 메시지는 이 실패 로그에 포함하지 않는다.
     */
    private void publishPlaylistContentAddedNotification(PlaylistContentAddedEvent event, UUID receiverId) {
        try {
            eventPublisher.publishEvent(
                    new NotificationCreateRequestedEvent(
                            receiverId,
                            "구독한 플레이리스트에 새 콘텐츠",
                            "\"%s\" 플레이리스트에 \"%s\" 콘텐츠가 추가되었습니다.".formatted(event.playlistTitle(), event.contentTitle()),
                            NotificationLevel.INFO)
            );
        } catch (RuntimeException e){
            log.warn(
                    "콘텐츠 추가 알림 변환·재발행 실패. playlistId={}, contentId={}, actorId={}, receiverId={}, exceptionType={}",
                    event.playlistId(),
                    event.contentId(),
                    event.actorId(),
                    receiverId,
                    e.getClass().getSimpleName()
            );
        }
    }

    /** null·빈 문자열·공백뿐인 이름은 기본값으로 대체하고, 그 외에는 앞뒤 공백도 그대로 유지한다. */
    private String resolveUserName(String name, String defaultName){
        if (!StringUtils.hasText(name)){
            return defaultName;
        }
        return name;
    }

    /** 팔로워·구독자·플레이리스트 소유자의 이름이 없을 때 사용하는 공통 표시명은 '사용자'다. */
    private String resolveUserName(String name){
       return resolveUserName(name, "사용자");
    }

}
