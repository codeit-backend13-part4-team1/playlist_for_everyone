package com.codeit.sb13.playlist_for_everyone.domain.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.request.WatchingSessionRequestByContent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WatchingSessionHttpService {

    private final ViewingSessionService viewingSessionService;
    private final ContentService contentService;
    private final UserService userService;

    /**
     * 현재 컨텐츠를 시청하고 있는 사용자의 초기 정보를 반환하는 매서드.
     */
    @Transactional(readOnly = true)
    public void getCurrentSessionInfo(WatchingSessionRequestByContent request){
        // 객체 null 검증. -  필드 입력값은 Dto 에서 이미 검증 함.
        Objects.requireNonNull(request);

        // 1. 현재 시청중인 userId 리스트 반환.
//        viewingSessionService.
    }

}
