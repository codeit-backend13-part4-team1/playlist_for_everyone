package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 대화방에서 필요한 사용자 조회를 UserService.getById에 위임하는 간단한 파사드 역할이다.
 *
 * <p>사용자 도메인 호출 지점을 모아 Saver가 UserRepository에 직접 의존하지 않도록 한다.
 * 별도 포트 인터페이스를 도입한 구조는 아니며, User 엔티티에 대한 의존성은 남아 있다.
 * 현재 UserService는 프로필 이미지 정보도 함께 조회하며, 이미지 URL 생성은 응답 변환 시 수행한다.
 * 조회 예외는 그대로 전달하고, 상대방 미존재 예외의 변환은 Saver가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class ConversationUserReader {

    private final UserService userService;

    public User getById(UUID userId){
        return userService.getById(userId);
    }
}
