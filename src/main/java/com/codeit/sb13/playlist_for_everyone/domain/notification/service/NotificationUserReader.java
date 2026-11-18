package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 알림에서 필요한 사용자 조회를 UserService.getById에 위임하는 컴포넌트다.
 *
 * <p>사용자 도메인 호출 지점을 모으는 작은 파사드 역할이며, 기존 서비스 계층 안의 역할 분리다.
 * 별도 포트 인터페이스는 두지 않고 User 엔티티를 반환한다.
 * 알림 저장 경로에서는 UserService의 조회가 NotificationService.save의 독립 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationUserReader {

    private final UserService userService;

    /** 사용자가 없으면 UserService의 UserNotFoundException을 변환 없이 호출자에게 전달한다. */
    public User getById(UUID userId){
        return userService.getById(userId);
    }
}
