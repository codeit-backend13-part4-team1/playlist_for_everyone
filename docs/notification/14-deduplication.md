# 14. 반복 알림 발송 제한

같은 행위가 제한 시간 안에 반복되면 알림 작업을 한 번만 제출합니다. 여러 서버가 같은 Redis를 사용하면 동일한 제한 키를 공유합니다.

## 제한 대상과 키

Redis 키는 `everyone:notification:deduplication:` 접두사와 아래 행위 키를 이어 붙여 만듭니다.

| 알림 유형 | 행위 키 | 수신자 |
| --- | --- | --- |
| 팔로우 | `follow:{followerId}:{followeeId}` | 팔로우받은 사용자 |
| 플레이리스트 구독 | `playlist-subscribe:{subscriberId}:{playlistId}` | 플레이리스트 소유자 |
| 플레이리스트 콘텐츠 추가 | `playlist-content-add:{playlistId}:{contentId}` | 플레이리스트 구독자 중 콘텐츠를 추가한 사용자를 제외한 사람 |
| 시청 시작 | `watching-session-start:{watcherId}:{contentId}` | 시청자의 팔로워 중 시청자 본인을 제외한 사람 |

기본 제한 시간은 60초입니다. `everyone.notification.deduplication.window`는 환경변수 `EVERYONE_NOTIFICATION_DEDUPLICATION_WINDOW`로 조정할 수 있습니다.

## Redis 선점과 작업 제출

1. 알림 리스너가 수신자를 결정합니다. 수신자가 있으면 `NotificationSubmitter`가 Redis에 행위 키를 선점합니다. 토큰과 만료 시간은 키가 없을 때 한 번에 저장합니다.
2. 선점에 성공하면 수신자 전체에 대한 알림 목록을 비동기 작업 하나로 제출합니다. 키가 이미 있으면 전체 알림 작업을 생략하고 기존 만료 시각을 유지합니다.
3. 실행기가 제출을 거부하면 `NotificationDeduplicationStore`가 저장된 토큰과 제출 요청의 토큰을 비교해 일치할 때만 키를 삭제합니다. 정상적으로 제출된 키는 알림 저장이나 SSE 발행에 실패하더라도 만료까지 유지됩니다.

알림 저장과 SSE 발행 요청은 [공통 생성·전송 흐름](01-creation-and-delivery.md)에서 설명합니다.

## 시청 시작 알림의 범위

알림 리스너는 전달받은 `WatchingSessionStartedEvent`에 시청자 ID와 콘텐츠 ID를 기준으로 제한을 적용합니다. 같은 사용자의 활성 탭이 `0 → 1`로 바뀔 때만 이벤트를 발행하는 규칙은 시청 도메인의 후속 작업입니다. [시청 시작 알림 연동](13-watching-session-integration.md)

[알림 가이드 목차](README.md)
