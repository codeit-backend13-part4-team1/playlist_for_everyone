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
`send`의 IOException·IllegalStateException은 실패한 연결의 상태·예약·저장소 참조를 정리합니다.

`sseHeartbeatTaskScheduler`는 스레드 한 개에서 로컬 연결별 작업을 제출하고 로그인 세션을 재검증합니다.
`fixedDelay`는 이 스케줄러 작업이 끝난 시점부터 다음 실행까지의 간격입니다.
실제 쓰기는 전용 `sseHeartbeatTaskExecutor`의 워커에서 실행합니다.
연결마다 대기하거나 실행 중인 작업은 하나입니다. 풀과 대기열이 가득 차면 해당 주기의 하트비트를 건너뜁니다.

주기적 재검증에서 폐기를 확인하면 업무 이벤트 전송 상태와 예약을 정리한 뒤 워커에 종료를 요청합니다.
이미 대기 중인 작업은 종료를 우선하고, 전송이 진행 중이면 해당 워커가 전송 후 `complete()`를 호출합니다.
포화로 거절된 하트비트는 해당 주기에 건너뜁니다. 거절된 종료 요청은 연결별로 보존합니다.
다음 주기에 보류된 종료를 일반 하트비트보다 먼저 재제출합니다. 종료 여부는 이전에 확인한 세션 폐기 결과에 따릅니다.
그사이에 연결이 저장소에서 제거됐다면 보류된 종료 요청도 정리합니다.

## 설정값과 허용 범위

아래 키는 everyone.sse 아래에 있습니다.

| 설정 | 환경변수 | 기본값 | 허용 범위 |
| --- | --- | --- | --- |
| heartbeat-interval | EVERYONE_SSE_HEARTBEAT_INTERVAL | 15s | 0보다 큰 값 |
| heartbeat.pool-size | EVERYONE_SSE_HEARTBEAT_POOL_SIZE | 4 | 2 이상 |
| heartbeat.queue-capacity | EVERYONE_SSE_HEARTBEAT_QUEUE_CAPACITY | 100 | 0 이상 |
| connection-timeout | EVERYONE_SSE_CONNECTION_TIMEOUT | 30m | 1ms 이상 |

대기열이 0이면 작업을 받을 수 있는 워커가 있을 때만 제출이 성공합니다.
연결 제한 시간은 emitter 생성 시 밀리초로 전달하는 비동기 요청 제한 시간입니다. 500us처럼 0ms가 되는 값은 거부합니다.
하트비트 전송은 이 제한 시간을 갱신하지 않습니다. 소켓 쓰기가 기다릴 수 있는 시간은 서블릿 I/O 설정에 따릅니다.
최종 바인딩 값이 누락되거나 유효하지 않으면 시작에 실패합니다.
[이력·재시도 설정](11-history-operations.md)은 별도 페이지에서 확인합니다.

구현: [전송 서비스](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryService.java),
[하트비트 스케줄러](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatScheduler.java).

---

[← 연결 생성과 종료](01-connection-lifecycle.md) · [목차](README.md) · [다음: 저장소와 동시성 →](03-repository-concurrency.md)
