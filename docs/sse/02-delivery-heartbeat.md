# 2. 이벤트 전송과 하트비트

SseDeliveryService는 현재 서버의 수신자 연결들을 찾아 후속 이력 조회를 요청합니다.
서버 간 전송은 공통 발행기와 Redis 수신 경로가 담당합니다.

## 이벤트가 클라이언트에 전달되는 형태

| SseEvent 필드 | 의미 | null 허용 |
| --- | --- | --- |
| receiverId | 이력 소유자·수신자 | 불가 |
| eventId | SSE 프레임 id, 재연결 커서 | 불가 |
| eventName | notifications 또는 direct-messages | 불가 |
| data | JSON으로 전달할 DTO 등 | 공통 계약은 허용 |

알림 도메인은 NotificationDto를 사용합니다. 다음은 형식 설명용 축약 예시입니다.

```text
id:550e8400-e29b-41d4-a716-446655440000
event:notifications
data:{"id":"d6d566d4-bdd3-408c-aedc-b2dd71e5ea80","title":"새 알림"}

```

호출부는 DTO를 미리 JSON 문자열로 만들지 않습니다.
이력 저장 발행기가 data를 JSON 트리로 변환하고, 복구 서비스가 저장된 data를 emitter에 전달합니다.
내부 SseEvent 전체를 클라이언트의 data로 감싸지 않습니다.
공통 data의 null 허용이 알림 필터에 필요한 data.id까지 생략할 수 있다는 뜻은 아닙니다.
알림의 ID를 읽을 수 없는 항목은 [복구 필터](10-recovery-and-retry.md#필터와-보관-범위-밖의-이력)에서 제외합니다.

같은 이벤트를 여러 탭에 보내도 이벤트 UUID는 유지합니다.
record의 data 객체까지 불변인 것은 아니므로 호출 후 payload를 변경하지 않습니다.

## 여러 연결에 보내다가 실패하면

```text
로컬 연결 조회 → 각 연결의 후속 이력 조회 → 도메인 필터 → 원본 순서로 전송
```

수신한 브로커 payload 자체를 직접 보내지 않습니다.
로컬 연결이 없으면 정상 종료하며, 공통 발행기에서 이미 저장한 이력은 유지됩니다.

| 실패 단계 | 처리 |
| --- | --- |
| 이벤트 생성의 필수값 null·지원하지 않는 이름 | 생성 실패 |
| 발행 전 JSON 변환·이력 저장 | SsePublishException 전달, 브로커 호출 없음 |
| 저장 후 브로커 발행 | 실패 전달, 기존 이력 유지, 자동 재발행 없음 |
| 후속 조회의 두 일시적 데이터 접근 예외 | 조정기가 연결을 유지하고 제한된 재시도 예약 |
| 로컬 전송 호출에서 전달된 그 외 RuntimeException | 조정기에 해당 연결의 HTTP 종료·정리를 요청한 후 다음 연결 처리 |
| 예약 재시도에서 전달된 그 외 RuntimeException | 상태·예약 정리 후 해당 emitter의 HTTP 종료·저장소 정리 요청 |

JSON 변환은 로컬 전송 단계가 아닌 공통 발행 단계에 있습니다.
필터·재시도·처리 위치의 상세 계약은 [복구 문서](10-recovery-and-retry.md)를 참고합니다.

## 단절과 오류의 로그 기준

DisconnectedClientHelper가 식별한 클라이언트 단절은 스택 없이 DEBUG로 기록합니다.
모든 IOException을 단절로 취급하지 않습니다.

| 위치 | 식별된 단절 | 그 외 오류 |
| --- | --- | --- |
| onError 콜백 | DEBUG | ERROR, 스택 포함 |
| 일반 이벤트·예약 전송 | DEBUG | WARN, 스택 포함 |
| 하트비트 전송 | DEBUG | WARN, 스택 포함 |

일반 전송 로그는 수신 신호의 이벤트 ID·수신자·연결 ID를, 예약 작업은 사용자·연결 ID를 사용합니다.
로그 수준에 관계없이 실패한 연결의 상태와 로컬 참조를 정리합니다.
[이력·재시도 로그](11-history-operations.md#실패-로그를-읽는-순서)와
[브로커 로그](06-redis-transport.md#실패-처리와-로그)는 단계별로 구분합니다.

## 하트비트 동작

하트비트는 ID와 이벤트 이름이 없는 주석이며 이력 저장·재전송 대상이 아닙니다.

```text
:heartbeat

```

응답을 받는 ping/pong이 아니라 실제 쓰기 실패를 통해 단절을 알아채는 방식입니다.
`send`의 IOException·IllegalStateException은 해당 상태·예약·저장소 참조를 정리하고 다음 연결로 진행합니다.
이 경로에서 complete를 추가 호출하지는 않습니다. 일반 이벤트 전송의 [종료 요청](01-connection-lifecycle.md#종료-요청과-참조-정리)과 구분합니다.

fixedDelay이므로 전송에 2초가 걸리면 기본값에서 다음 시작은 이전 시작으로부터 약 17초 뒤입니다.
하트비트가 HTTP 연결 타임아웃을 갱신하지는 않습니다.
전용 sseHeartbeatTaskScheduler는 스레드 한 개이며, 재시도 전용 실행기와 별개입니다.
defaultCandidate=false인 빈을 이름으로 지정하여 사용합니다.

## 설정값과 허용 범위

아래 키는 everyone.sse 아래에 있습니다.

| 설정 | 환경변수 | 기본값 | 허용 범위 |
| --- | --- | --- | --- |
| heartbeat-interval | EVERYONE_SSE_HEARTBEAT_INTERVAL | 15s | 0보다 큰 값 |
| connection-timeout | EVERYONE_SSE_CONNECTION_TIMEOUT | 30m | 1ms 이상 |

연결 제한 시간은 emitter 생성 시 밀리초로 전달합니다. 500us처럼 0ms가 되는 값은 거부합니다.
최종 바인딩 값이 누락되거나 유효하지 않으면 시작에 실패합니다.
[이력·재시도 설정](11-history-operations.md)은 별도 페이지에서 확인합니다.

구현: [전송 서비스](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryService.java),
[하트비트 스케줄러](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatScheduler.java).

---

[← 연결 생성과 종료](01-connection-lifecycle.md) · [목차](README.md) · [다음: 저장소와 동시성 →](03-repository-concurrency.md)
