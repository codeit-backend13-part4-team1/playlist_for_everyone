package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.ViewingSessionInfoDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 동시 시청 세션의 시청 기능 관련 주요 기능들을 정의하는 인터페이스
 * 세션 룸의 기준이 될 정보를 받아 세션 룸을 조작하고,
 * 세션 정보와 컨텐츠 id 를 파라매터로 받아서 룸과 세션의 연결을 행하는 기능 계약이다.
 */
public interface ViewingSessionService {

    /**
     * 세션 방에 참가하는 매서드.
     * @param subscription 구독 정보
     */
    void join(WSSubscription subscription, UUID contentId, UUID userId);

    /**
     * 참가된 세션 방에서 나가는 매서드.
     * @param subscription 구독 정보
     */
    void leave(WSSubscription subscription, UUID contentId);

    /**
     * 해당 구독 리스트를 특정 세션 룸에서 등록 취소하는 매서드
     * @param subscriptions 구독 리스트
     * @param contentId 대상이 되는 컨텐츠 id
     */
    void leaveAll(List<WSSubscription> subscriptions, UUID contentId);

    /**
     * 특정 컨텐츠에 대한 세션 룸 및 리소스를 해지하는 매서드
     *
     * @param contentId 대상이 되는 컨텐츠 id
     * @return
     */
    List<WSSubscription> destroy(UUID contentId);

    /**
     * 세션 룸에 메세지를 전송하는 매서드.
     * @param payload 전송 할 내용
     */
    void send(UUID contentId, Object payload);

    /**
     * 인자로 받은 세션 id 에 대한 세션 룸 내에서의 수명을 갱신하는 매서드
     * @param subscriptionKeys 구독 uuid 들
     */
    void refresh(List<UUID> subscriptionKeys);

    /**
     * 컨텐츠 id 를 대상으로 현재 시청자 수를 카운트 하는 매서드
     * @param contentId 대상이 되는 컨텐츠 id
     */
    long getCurrentViewerCount(UUID contentId);

    /**
     * 인자로 받은 컨텐츠 id 리스트에 대해 각 컨텐츠의 현재 시청자 수를 반환하는 매서드
     * @param contentIdList 리스트로 된 조회 대상 컨텐츠
     * @return contentId 와 각 컨텐츠에 해당하는 시청자 수
     */
    Map<UUID, Long> getCurrentViewerCountWithContents(List<UUID> contentIdList);


    /**
     * 인자로 받은 컨텐츠에 대해 현재 시청중인 시청자 id 의 리스트를 반환하는 매서드.
     * @param contentId 대상이 되는 컨텐츠 id
     */
    List<ViewingSessionInfoDto> getCurrentViewerIdList(UUID contentId);

    /**
     * 인자로 받은 유저에 대한 활성 세션 정보를 반환하는 매서드
     */
    Optional<ViewingSessionInfoDto> queryViewerId(UUID userId);
}
