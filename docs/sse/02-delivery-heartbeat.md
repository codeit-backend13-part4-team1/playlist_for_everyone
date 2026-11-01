# 2. 이벤트 전송과 하트비트

`SseDeliveryService`는 수신자의 **현재 서버 연결들**을 찾아 같은 이벤트를 보냅니다.
다른 서버로 메시지를 전달하는 책임은 향후 `SseEventPublisher` 구현체가 담당합니다.

## 이벤트가 클라이언트에 전달되는 형태

| `SseEvent` 필드 | 사용처 | null 허용 |
| --- | --- | --- |
| `receiverId` | 저장소에서 수신자의 연결들을 찾습니다. | 불가 |
| `eventId` | SSE 프레임의 `id`에 기록합니다. | 불가 |
| `eventName` | SSE 프레임의 `event`에 기록합니다. | 불가 |
| `data` | JSON으로 변환하여 SSE 프레임의 `data`에 기록합니다. | 허용 |

다음은 전송 형식 설명용 예시입니다. 실제 알림 DTO의 전체 필드를 뜻하지 않습니다.

```text
id:550e8400-e29b-41d4-a716-446655440000
event:notifications
data:{"message":"테스트 알림"}

```

DM 이벤트 이름은 `direct-messages`입니다. `Event`의 enum 상수명 대신 `getName()` 값을 사용합니다.

**호출부**는 `SseEvent`의 `data`에 DTO 등의 객체를 그대로 넣습니다.
호출부에서 DTO를 JSON 문자열로 바꾸면 서비스가 그 문자열을 다시 변환하여 이중 인코딩될 수 있습니다.
**전송 서비스**는 애플리케이션의 `ObjectMapper`로 `data`만 JSON으로 한 번 변환하고, 준비한 문자열을 모든 수신 연결에 재사용합니다.
`SseEvent` 전체를 JSON 데이터로 보내지는 않습니다.

같은 이벤트를 여러 탭에 보내도 이벤트 ID는 유지합니다. 연결 ID는 메시지 ID로 사용하지 않습니다.
`SseEvent`는 record지만 내부 `data` 객체까지 불변으로 만들지는 않습니다. 전달 후 payload를 변경하지 않습니다.

## 여러 연결에 보내다가 실패하면

```text
수신자의 로컬 연결 조회 → data를 JSON으로 한 번 변환 → 각 연결에 별도 builder로 전송
```

현재 수신자 연결이 없으면 JSON 변환 없이 종료하며, 이벤트 이력 저장이나 재시도를 수행하지 않습니다.
실패 처리는 예외가 발생한 단계에 따라 구분합니다.

| 실패 단계 | 원인·예외 | 처리 |
| --- | --- | --- |
| 이벤트 생성 | 필수 필드가 null | 한글 메시지의 `NullPointerException`으로 생성을 중단합니다. |
| JSON 변환 | `JacksonException` | WARN 로그를 남기고 해당 이벤트 전송만 건너뜁니다. 연결은 유지합니다. |
| 각 연결의 `send()` | `IOException`, `IllegalStateException` | 실패한 연결만 삭제하고 다음 연결에 계속 전송합니다. |

JSON 변환 실패 시에는 아직 어떤 연결에도 데이터를 쓰지 않았으므로 연결을 제거하지 않습니다.
변환에 성공했어도 조회했던 연결이 그사이 종료될 수 있어 `send()`의 예외 처리는 유지합니다.
하트비트도 실제 전송에 실패한 연결만 정리하고 나머지 연결에 계속 전송합니다.

## 단절과 오류의 로그 기준

JSON 변환 실패는 이벤트·수신자 ID와 예외 스택을 `WARN`으로 남기며 연결을 유지합니다.
아래 표는 실제 연결 전송과 `onError` 콜백에서 발생한 오류의 로그 기준입니다.

`DisconnectedClientHelper`가 클라이언트 단절로 식별한 예외는 스택 없이 식별 정보와 정리 메시지를 `DEBUG`로 남깁니다.
브라우저 종료 등으로 반복되는 단절 로그를 줄이되, 다른 오류의 원인은 추적할 수 있도록 구분합니다.
모든 `IOException`을 클라이언트 단절로 취급하지 않습니다.

| 발생 위치 | 식별된 클라이언트 단절 | 그 외 오류 |
| --- | --- | --- |
| 연결의 `onError` 콜백 | `DEBUG`, 스택 제외 | `ERROR`, 스택 포함 |
| 업무 이벤트 전송 | `DEBUG`, 스택 제외 | `WARN`, 스택 포함 |
| 하트비트 전송 | `DEBUG`, 스택 제외 | `WARN`, 스택 포함 |

위 표의 전송 실패와 `onError`에서는 로그 수준에 관계없이 해당 연결을 저장소에서 제거합니다.
전송 서비스는 나머지 연결에 대한 전송을 계속하고, `onError`의 비동기 응답 종료는 MVC가 관리합니다.
초기 연결 실패와 서버 종료 처리의 예외 흐름은 [연결 생성과 종료](01-connection-lifecycle.md#초기-실패와-서버-종료)를 참고합니다.

## 하트비트 동작

하트비트는 모든 로컬 연결에 아래 주석 프레임을 주기적으로 보냅니다.

```text
:heartbeat

```

`id`와 `event`를 넣지 않으므로 알림·DM 이벤트로 처리되지 않고 마지막 이벤트 ID도 바뀌지 않습니다.
전송 실패가 확인되면 해당 연결을 정리합니다. 응답을 받는 ping/pong 방식이 아니므로 브라우저 종료를 즉시 확정하는 기능은 아닙니다.

`fixedDelay`이므로 전송에 2초가 걸리면 기본 설정에서 다음 시작은 이전 시작으로부터 약 17초 뒤입니다.
하트비트가 연결 제한 시간을 갱신하는 것은 아닙니다. 제한 시간으로 연결이 끝나면 클라이언트가 재연결해야 합니다.

스케줄러는 `sseHeartbeatTaskScheduler`라는 이름으로 지정하며 스레드 한 개를 사용합니다.
`defaultCandidate=false`는 타입만으로 주입할 때 기본 후보에서 제외한다는 뜻입니다. 이름으로 지정한 하트비트 작업에는 사용됩니다.
각 서버가 자기 연결에 하트비트를 보내고, 스케줄러 자원 정리는 Spring이 담당합니다.

## 설정값과 허용 범위

아래 설정 키의 공통 접두사는 `everyone.sse`입니다. 개발에서는 `.env.dev`, 운영에서는 외부 환경변수로 지정합니다.
`15s`, `30m`처럼 단위를 포함한 Duration 형식을 사용합니다.

| 설정 키 | 환경변수 | 기본값 | 허용 범위 |
| --- | --- | --- | --- |
| `heartbeat-interval` | `EVERYONE_SSE_HEARTBEAT_INTERVAL` | `15s` | 0보다 큰 값 |
| `connection-timeout` | `EVERYONE_SSE_CONNECTION_TIMEOUT` | `30m` | 1ms 이상 |

`heartbeat-interval`은 하트비트 작업 완료 후 다음 실행까지의 대기 시간입니다.
`connection-timeout`은 개별 비동기 HTTP 요청의 제한 시간이며, emitter에 전달할 때 밀리초로 변환합니다.
예를 들어 `500us`는 양수여도 `toMillis()` 결과가 0이 되므로 허용하지 않습니다.

환경변수를 생략하면 [application.yaml](../../src/main/resources/application.yaml)의 기본값을 사용합니다.
최종 바인딩 값이 `null`이거나 허용 범위를 벗어나면 `SseProperties` 생성자가 거부하여 컨텍스트 초기화, 즉 애플리케이션 시작에 실패합니다.
기본 YAML을 읽지 않고 값을 직접 제공하는 테스트의 누락 사례는 [설정 바인딩 테스트](05-testing.md#설정-바인딩-테스트)를 참고합니다.

## 서버 간 전송과의 경계

현재 `SseEventPublisher`는 `publish(SseEvent<?>)` 계약만 있으며 구현체는 없습니다.
향후 Redis·Kafka에서 이벤트를 수신한 각 서버가 로컬 전송 서비스를 호출하는 구성을 위한 인터페이스입니다.
이 경우에도 실제 HTTP 연결을 가진 emitter는 연결된 서버의 메모리에 남습니다.

이벤트 저장·재전송과 알림·DM 생성 후의 연동은 후속 구현입니다.
현재 `publish` 계약에는 브라우저 수신 확인을 반환하는 기능이 없습니다.

## 관련 코드와 테스트

- [SseDeliveryService](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryService.java), [SseEvent](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/event/SseEvent.java), [SseEventPublisher](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/event/SseEventPublisher.java): 전송 동작과 공통 계약입니다.
- [SseHeartbeatScheduler](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatScheduler.java), [SchedulerConfig](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/SchedulerConfig.java): 실행 간격과 전용 실행기 설정입니다.
- [SseProperties](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/config/SseProperties.java), [SsePropertiesTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/config/SsePropertiesTest.java): 설정값의 허용 범위와 바인딩 성공·실패를 확인합니다.
- [SseDeliveryServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryServiceTest.java): 메시지 필드, 다중 연결, 실패 후 계속 전송하는 동작을 검증합니다.

---

[← 이전: 연결 생성과 종료](01-connection-lifecycle.md) · [목차](README.md) · [다음: 저장소와 동시성 →](03-repository-concurrency.md)
