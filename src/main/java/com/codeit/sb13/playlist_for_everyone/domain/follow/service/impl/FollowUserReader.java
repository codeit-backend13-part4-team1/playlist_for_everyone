package com.codeit.sb13.playlist_for_everyone.domain.follow.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 팔로우에서 필요한 사용자 조회를 UserService.getById에 위임하는 컴포넌트.
 * 사용자 도메인 앞에 단순한 조회 진입점을 제공하는 간단한 파사드 역할을 한다.
 *
 * <p>사용자 도메인 호출 지점을 모아 FollowSaver가 UserRepository에 직접 의존하지 않도록 한다.
 * 반환 타입으로 User 엔티티를 사용하므로 사용자 엔티티에 대한 의존성은 남아 있다.
 * 별도 포트 인터페이스를 도입한 구조는 아니며, 기존 서비스 계층 안의 역할 분리다.
 * 사용자 미존재 예외는 그대로 전달하고, 팔로우 대상에 맞춘 예외 변환은 FollowSaver가 담당한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FollowUserReader {

    private final UserService userService;

    public User getById(UUID userId){
        return userService.getById(userId);
    }
}
