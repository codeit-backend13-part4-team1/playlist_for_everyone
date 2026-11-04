package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;

/**
 * 동시 시청 도메인의 서비스를 큰 틀로 담당하는 클래스.
 * WatchingSessionService, UserService, ContentService 를 하나의 래퍼로 감싸서, 여청단위 클래스로 반환한다.
 */

public class WatchingSession {

    /**
     * 세션 정보를 바탕으로, 동시 시청 채널에 참여(구독) 하고
     * 같은 경로에 대해 현재 채널 정보와, 참여한 시청자 정보 및, 시청중인 컨텐츠 정보를
     * WatchingSessionChange 로 반환해는 매서드.
     * @param info 채널 인터셉터에서 전달 된 프레임 정보.
     * @return WatchingSessionChange 세션에 일어난 변화 정보와, 세션 내부 정보를 담고있는 객체.
     */
    public WatchingSessionChange joinViewerSession(SessionInfo info){
        return null;
    }

    /**
     * 세션 정보를 바탕으로, 동시 시청 채널에 이탈(구독 취소) 하고
     * 같은 경로에 대해 현재 채널 정보와, 참여한 시청자 정보 및, 시청중인 컨텐츠 정보를
     * WatchingSessionChange 로 반환해는 매서드.
     * @param info 채널 인터셉터에서 전달 된 프레임 정보.
     * @return WatchingSessionChange 세션에 일어난 변화 정보와, 세션 내부 정보를 담고있는 객체.
     */
    public WatchingSessionChange leaveViewerSession(SessionInfo info){
        return null;
    }

}
