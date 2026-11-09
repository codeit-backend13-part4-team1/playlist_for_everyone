package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDomainEventListener {

    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener
    public void handleUserFollowed(UserFollowedEvent event){
        try {
            String followerName = StringUtils.hasText(event.followerName()) ? event.followerName() : "사용자";
            NotificationCreateRequestedEvent requestedEvent = new NotificationCreateRequestedEvent(
                    event.followeeId(),
                    "새로운 팔로워",
                    "%s님이 회원님을 팔로우했습니다.".formatted(followerName),
                    NotificationLevel.INFO);
            eventPublisher.publishEvent(requestedEvent);
        } catch (RuntimeException e){
            log.warn(
                    "팔로우 알림 변환·재발행 실패. followerId={}, followeeId={}, exceptionType={}",
                    event.followerId(),
                    event.followeeId(),
                    e.getClass().getSimpleName()
            );
        }
    }

}
