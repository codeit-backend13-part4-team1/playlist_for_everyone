package com.codeit.sb13.playlist_for_everyone.global.sse.replay;

import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;

import java.util.List;
import java.util.UUID;

/**
 * 초기 복구와 후속 조회에서 도메인별로 전송 가능한 이력을 고르는 계약.
 * 공통 복구 서비스가 이벤트 이름별로 묶어 호출하며, 현재는 처음 지원하는 필터 하나를 사용한다.
 * 구현체는 입력 이력의 일부를 반환한다. 원래 전송 순서와 처리 위치의 진행은 복구 서비스가 관리한다.
 * 조회 예외는 빈 목록으로 숨기지 않고 호출자에게 전달한다.
 */
public interface SseReplayFilter {
    boolean supports(String eventName);

    List<SseHistoryEntry> filter(UUID receiverId, List<SseHistoryEntry> entries);
}
