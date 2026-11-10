package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 알림에 필요한 팔로워·구독자 조회를 한곳에 모으는 단순 파사드 역할의 컴포넌트.
 * 기존 서비스 계층 안에서 FollowService와 PlaylistService의 조회 계약을 연결한다.
 *
 * <p>조회 결과와 예외는 호출자에게 그대로 전달한다. 수신자 중복 제거·행위자 제외와
 * 조회 실패 처리는 알림 정책을 결정하는 NotificationDomainEventListener가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationTargetReader {
    private final FollowService followService;
    private final PlaylistService playlistService;

    /** 대상 사용자를 팔로우하는 사용자 ID 목록을 반환한다. 관계가 없으면 빈 목록이며 순서는 보장하지 않는다. */
    public List<UUID> findFollowerIds(UUID followeeId){
        return followService.findFollowerIds(followeeId);
    }

    /** 플레이리스트 구독자 ID 목록을 반환한다. 관계가 없으면 빈 목록이며 순서는 보장하지 않는다. */
    public List<UUID> findSubscriberIds(UUID playlistId){
        return playlistService.findSubscriberIds(playlistId);
    }
}
