package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 한 업무 이벤트에서 만든 알림 요청 목록과 실패 추적용 업무 식별자다.
 * 작업 제출 전에 불변 복사하여 작업 스레드에서도 동일한 수신자·문구를 사용한다.
 * requests에는 사용자 정보가 포함되므로 배치 객체 전체를 로그에 출력하지 않는다.
 */
public record NotificationPublishBatch(
        String source,
        Map<String, UUID> sourceIds,
        List<NotificationCreateRequestedEvent> requests
) {
    public NotificationPublishBatch {
        sourceIds = Map.copyOf(sourceIds);
        requests = List.copyOf(requests);
    }
}
