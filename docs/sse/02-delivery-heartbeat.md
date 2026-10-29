# 2. 이벤트 전송과 하트비트

`SseDeliveryService`는 수신자의 **현재 서버 연결들**을 찾아 같은 이벤트를 보냅니다.
다른 서버로 메시지를 전달하는 책임은 향후 `SseEventPublisher` 구현체가 담당합니다.

## 이벤트가 클라이언트에 전달되는 형태

| `SseEvent` 필드 | 사용처 |
| --- | --- |
| `receiverId` | 저장소에서 수신자의 연결들을 찾습니다. |
| `eventId` | SSE 프레임의 `id`에 기록합니다. |
| `eventName` | SSE 프레임의 `event`에 기록합니다. |
| `data` | 객체를 JSON으로 직렬화하여 SSE 프레임의 `data`에 기록합니다. |

다음은 전송 형식 설명용 예시입니다. 실제 알림 DTO의 전체 필드를 뜻하지 않습니다.

```text
id:550e8400-e29b-41d4-a716-446655440000
event:notifications
data:{"message":"테스트 알림"}

```

DM 이벤트 이름은 `direct-messages`입니다. `Event`의 enum 상수명 대신 `getName()` 값을 사용합니다.
전송하는 JSON은 `SseEvent` 전체가 아니라 `data` 객체입니다.
DTO를 미리 JSON 문자열로 바꾸면 이중 인코딩될 수 있으므로 객체 그대로 전달합니다.

같은 이벤트를 여러 탭에 보내도 이벤트 ID는 유지합니다. 연결 ID는 메시지 ID로 사용하지 않습니다.
`SseEvent`는 record지만 내부 `data` 객체까지 불변으로 만들지는 않습니다. 전달 후 payload를 변경하지 않습니다.

## 여러 연결에 보내다가 실패하면

```text
수신자의 로컬 연결 조회 → 각 연결에 별도 이벤트 builder로 전송
                        ├─ 성공: 다음 연결 전송
                        └─ I/O·상태 예외: 해당 연결 삭제 후 다음 연결 전송
```

`IOException` 또는 `IllegalStateException`이 발생한 연결만 삭제하고 로그를 남깁니다.
한 탭의 전송 실패가 다른 탭이나 사용자의 전송을 중단하지 않도록 합니다.
이벤트와 하트비트는 각각 자신의 순회에서 이 원칙을 적용합니다.

조회 당시 있던 연결도 실제 전송 시점에는 종료되었을 수 있어 전송 예외 처리가 필요합니다.
현재 수신자 연결이 없으면 예외 없이 끝나며, 이벤트 이력 저장이나 재시도를 수행하지 않습니다.

## 하트비트와 설정

하트비트는 모든 로컬 연결에 아래 주석 프레임을 주기적으로 보냅니다.

```text
:heartbeat

```

`id`와 `event`를 넣지 않으므로 알림·DM 이벤트로 처리되지 않고 마지막 이벤트 ID도 바뀌지 않습니다.
전송 실패가 확인되면 해당 연결을 정리합니다. 응답을 받는 ping/pong 방식이 아니므로 브라우저 종료를 즉시 확정하는 기능은 아닙니다.

| 환경변수 | 기본값 | 의미 |
| --- | --- | --- |
| `EVERYONE_SSE_HEARTBEAT_INTERVAL` | `15s` | 하트비트 작업 완료 후 다음 실행까지 기다리는 시간 |
| `EVERYONE_SSE_CONNECTION_TIMEOUT` | `30m` | 개별 SSE 비동기 요청의 제한 시간 |

설정은 `everyone.sse` 아래에 바인딩됩니다. 개발에서는 `.env.dev`, 운영에서는 외부 환경변수로 지정합니다.
`15s`, `30m`처럼 Duration 형식을 사용합니다. 기본값은 [application.yaml](../../src/main/resources/application.yaml)에 있습니다.

`fixedDelay`이므로 전송에 2초가 걸리면 기본 설정에서 다음 시작은 이전 시작으로부터 약 17초 뒤입니다.
하트비트가 연결 제한 시간을 갱신하는 것은 아닙니다. 제한 시간으로 연결이 끝나면 클라이언트가 재연결해야 합니다.

스케줄러는 `sseHeartbeatTaskScheduler`라는 이름으로 지정하며 스레드 한 개를 사용합니다.
`defaultCandidate=false`는 타입만으로 주입할 때 기본 후보에서 제외한다는 뜻입니다. 이름으로 지정한 하트비트 작업에는 사용됩니다.
각 서버가 자기 연결에 하트비트를 보내고, 스케줄러 자원 정리는 Spring이 담당합니다.

## 서버 간 전송과의 경계

현재 `SseEventPublisher`는 `publish(SseEvent<?>)` 계약만 있으며 구현체는 없습니다.
향후 Redis·Kafka에서 이벤트를 수신한 각 서버가 로컬 전송 서비스를 호출하는 구성을 위한 인터페이스입니다.
이 경우에도 실제 HTTP 연결을 가진 emitter는 연결된 서버의 메모리에 남습니다.

이벤트 저장·재전송과 알림·DM 생성 후의 연동은 후속 구현입니다.
현재 `publish` 계약에는 브라우저 수신 확인을 반환하는 기능이 없습니다.

## 관련 코드와 테스트

- [SseDeliveryService](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryService.java), [SseEvent](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/event/SseEvent.java), [SseEventPublisher](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/event/SseEventPublisher.java): 전송 동작과 공통 계약입니다.
- [SseHeartbeatScheduler](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatScheduler.java), [SchedulerConfig](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/SchedulerConfig.java): 실행 간격과 전용 실행기 설정입니다.
- [SseDeliveryServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryServiceTest.java): 메시지 필드, 다중 연결, 실패 후 계속 전송하는 동작을 검증합니다.

---

[← 이전: 연결 생성과 종료](01-connection-lifecycle.md) · [목차](README.md) · [다음: 저장소와 동시성 →](03-repository-concurrency.md)
