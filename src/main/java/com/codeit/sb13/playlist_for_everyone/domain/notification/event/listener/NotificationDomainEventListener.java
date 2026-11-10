package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 팔로우·권한 변경·플레이리스트 구독 이벤트를 공통 알림 생성 요청으로 변환하여 재발행한다.
 * 생산 도메인은 업무 사실을 전달하고, 이 리스너가 수신자와 알림 문구를 결정한다.
 *
 * <p>DB 업무 이벤트는 기본 AFTER_COMMIT, fallbackExecution=false로 수신한다.
 * 업무가 롤백되거나 트랜잭션 없이 이벤트를 발행하면 알림 생성 요청으로 변환하지 않는다.
 *
 * <p>변환을 위한 별도 트랜잭션은 열지 않는다. 커밋 후 재발행한 요청은
 * fallbackExecution=true인 NotificationEventListener가 받아 저장 서비스에 전달한다.
 * 실제 알림 저장은 NotificationService.save의 REQUIRES_NEW 트랜잭션에서 수행한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDomainEventListener {

    private final ApplicationEventPublisher eventPublisher;

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

    /** null·빈 문자열·공백뿐인 이름은 기본값으로 대체하고, 그 외에는 앞뒤 공백도 그대로 유지한다. */
    private String resolveUserName(String name, String defaultName){
        if (!StringUtils.hasText(name)){
            return defaultName;
        }
        return name;
    }

    /** 팔로우·구독 알림에서 이름이 없을 때 사용하는 공통 표시명은 '사용자'다. */
    private String resolveUserName(String name){
       return resolveUserName(name, "사용자");
    }

}
