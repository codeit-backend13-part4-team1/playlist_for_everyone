package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DpeWatchingSessionService {

    /**
     * 현재 컨텐츠의 시청중인 동시 시청자 수를 반환하는 매서드
     * @param contentId 컨텐츠 엔티티의 PK 가 되는 키값
     * @return 현재 시청중인 시청자 수
     */
    long getCurrentViewerCount(UUID contentId);

    /**
     * 현재 컨텐츠의 시청중인 동시 시청자 수를 컨텐츠 id 별로 매핑해서
     * 반환하는 매서드.
     * @param contentIds 조회할 content 들의 id 리스트
     * @return contentId - counter 쌍.
     */
    Map<String, Long> getCurrentViewerCountWithContents(List<UUID> contentIds);

}
