package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.viewing;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;

import java.util.Optional;
import java.util.UUID;

/**
 * 동시 시청 도메인의 시청 부분 룸에 대한 정보를 저장하는 레파지토리.
 * 세션 룸의 정보를 저장, 조회, 삭제하는 역할을 담당하는 작업 사양이다.
 *
 */
public interface ViewingSessionRepository {

    void remove(ViewingSessionRoom room);

    ViewingSessionRoom findOrCreate(UUID contentId);

    Optional<ViewingSessionRoom> find(UUID contentId);

    boolean exists(UUID contentId);
}
