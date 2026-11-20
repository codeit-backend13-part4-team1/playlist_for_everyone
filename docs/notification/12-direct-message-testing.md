# 12. DM 알림 테스트 읽기와 실행

[#159](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/159)의 검증은 **문구 변환 → 실제 커밋과 실패 격리 → Redis·HTTP 수신**으로 나눕니다.
기능 계약과 전체 흐름은 [DM 알림 연동](11-direct-message-integration.md)을 먼저 읽습니다.

## 테스트별 책임

| 테스트 | 실제 검증 범위 | 대체하거나 포함하지 않는 것 |
| --- | --- | --- |
| [NotificationDomainEventListenerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/event/listener/NotificationDomainEventListenerTest.java) | DM 수신자·문구·수준, 이름 대체, 자기 알림 제외, 알림 요청 제출 | 프로세서는 Mock. 리스너를 직접 호출해 제출한 목록을 검증 |
| [DirectMessageNotificationAsyncIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/event/listener/DirectMessageNotificationAsyncIntegrationTest.java) | 알림 지연 중 송신 결과 반환, 제출 거부에도 DM 커밋·WebSocket 발행 유지 | 실제 트랜잭션과 전용 실행기를 사용하고 WebSocket 발행은 Mock으로 관찰 |
| [DirectMessageSseEventIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/event/listener/DirectMessageSseEventIntegrationTest.java) | 실제 DM·알림 저장과 커밋·롤백, 미접속 알림, 전송 경로별 실패 격리 | 파일 저장소·SSE/WebSocket 발행기는 Mock. 실제 네트워크 수신 없음 |
| [DirectMessageSseIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/DirectMessageSseIntegrationTest.java) | 실제 DM 저장부터 알림 DB·공유 이력·Redis·인증된 HTTP SSE 수신, STOMP 동시 구독 | 실제 브라우저 화면·로그인 API·운영 부하 검증 없음 |

기존 송신·WebSocket 계약은 `DirectMessageSendServiceIntegrationTest`와 `DirectMessageWebSocketIntegrationTest`를 함께 실행해 확인할 수 있습니다.

## 단위 테스트에서 확인하는 규칙

- 수신자·`새로운 메시지` 제목·이벤트 이름 스냅샷을 사용한 내용·`INFO` 수준을 확인합니다.
- 이름이 null·빈 문자열·공백·탭·개행이면 `사용자`로 대체합니다.
- 요청은 목록에 담아 한 번 제출합니다. 발신자와 수신자가 같으면 작업을 제출하지 않습니다.

리스너를 직접 호출하므로 이 결과만으로 `AFTER_COMMIT` 동작이나 DB 저장을 보장하지 않습니다.

## 실제 트랜잭션과 실패 격리

`DirectMessageSseEventIntegrationTest`는 PostgreSQL 16·Flyway·실제 저장 서비스를 사용하며, 클래스 전체에 테스트 트랜잭션을 걸지 않습니다.
커밋 전·롤백·외부 트랜잭션은 `TransactionTemplate`으로 제어하고, DM과 알림의 독립 저장 결과를 JDBC로 확인합니다.
알림 저장·전송 여부는 `NotificationTaskTracker`로 워커의 작업 완료를 기다린 뒤 확인합니다.

| 검증 묶음 | 확인할 결과 |
| --- | --- |
| 정상 커밋·미접속 | 알림 한 건, 정확한 수신자·문구·수준, DM·알림 SSE 발행 요청 각각 한 번 |
| 커밋 전·롤백·DM DB 저장 실패 | 알림·SSE 없음. 업무 이벤트 발행 기록만으로 소비 성공을 판단하지 않음 |
| 트랜잭션 밖 업무 이벤트 | 이미 존재하는 DM은 유지하고 알림·SSE 미실행 |
| 독립 저장 후 외부 롤백 | 커밋한 DM·알림과 이미 수행된 SSE 발행 유지 |
| 신규 실패 3건 | 알림 요청 재발행·알림 저장·알림 SSE 실패에도 DM·DM SSE·WebSocket 경로 유지 |
| 기존 실패 보강 5건 | DM SSE 조회·프로필 URL·발행 실패, WebSocket용 프로필 URL·발행 실패에도 알림 저장과 알림 SSE 요청 유지 |

SSE 호출은 이벤트 이름별로 구분하여 정확히 한 번의 **발행 요청·시도**를 검증합니다. Mock 호출 성공을 브라우저 수신으로 해석하지 않습니다.
WebSocket용 변환·발행 실패는 기존 오류와 `persisted=true`를 유지하고, DM·알림을 다시 저장하거나 앞선 SSE를 재시도하지 않는지도 확인합니다.
서로 다른 이벤트의 발행 순서를 고정하지 않습니다.

### Spy와 시각 비교

저장소와 공통 알림 요청 리스너의 Spy는 기본적으로 실제 동작합니다. 실패 테스트에서만 지정한 메서드에 예외를 주입하고 다음 테스트에서는 초기화합니다.
알림 저장 실패는 `NotificationRepository.save` 호출에 `DataIntegrityViolationException`을 주입하는 **저장 경계 검증**입니다.
실제 PostgreSQL 제약·커밋 오류를 유도한 것은 아니며, 공통 알림의 실제 DB 실패 검증은 [공통 트랜잭션 테스트](02-testing.md#커밋롤백-검증-읽기)에 있습니다.

DM DTO는 상세 조회·refresh 후 DB 시각과 일치하는지 확인합니다. 알림 DTO는 저장 시 스냅샷이므로 PostgreSQL의 마이크로초 정밀도를 고려해 DB 시각과 1,000ns 이내로 비교합니다.
정상 커밋 테스트는 고정 나노초 시각으로 이 차이를 재현하며, 종료 시 감사 시각 공급자를 복원합니다.

## 실제 전송 검증

`DirectMessageSseIntegrationTest`는 PostgreSQL·Redis Testcontainers와 실제 HTTP 포트를 사용합니다.
실제 DM을 저장한 뒤 `direct-messages`와 `notifications` 프레임을 모두 읽고, 이벤트 ID·이름·data를 공유 이력과 대조합니다.
알림 이력의 `data.id`는 수신자의 DB 알림 ID와 비교합니다. SSE 이벤트 ID와 DB 알림 ID는 별도 값입니다.

- WebSocket 구독 중에도 알림 한 건과 두 SSE 이벤트를 받는지 확인합니다.
- 다중 SSE 연결과 서버 A에서 저장한 메시지의 서버 B 수신을 검증합니다. 두 서버는 같은 JVM의 독립 Spring 컨텍스트입니다.
- Redis WebSocket 수신이 메시지 저장·업무 이벤트 발행·알림 생성을 중복 실행하지 않는지 확인합니다.
- 이력 복구 시 DM뿐 아니라 함께 저장된 알림 프레임도 소비·검증합니다.

중복 미수신은 정해진 관찰 시간 안에서 검증합니다. 모든 장애 상황의 exactly-once 보장이나 실제 브라우저 화면 확인을 뜻하지 않습니다.
과거 [브라우저 수동 검증 기록](../conversation/14-websocket-multi-node.md)은 #62 범위이며 이번 알림 검증과 구분합니다.

## 실행 방법

단위 테스트만 실행할 때는 Docker가 필요하지 않습니다.

```bash
./gradlew test --tests '*NotificationDomainEventListenerTest'
```

Docker를 실행한 뒤 DM 알림과 기존 송신·WebSocket 경계를 함께 확인합니다. 로컬 DB·Redis 대신 테스트 컨테이너를 사용합니다.

```bash
./gradlew test --tests '*NotificationDomainEventListenerTest' \
  --tests '*DirectMessageNotificationAsyncIntegrationTest' \
  --tests '*DirectMessageSseEventIntegrationTest' \
  --tests '*DirectMessageSseIntegrationTest' \
  --tests '*DirectMessageSendServiceIntegrationTest' \
  --tests '*DirectMessageWebSocketIntegrationTest'
```

## 확인한 실행 기록

2026-11-19에 아래 5개 클래스로 **105건 통과, 실패·오류·건너뜀 0건**을 확인했습니다. 아래 표는 당시 실행 범위를 기록합니다.

| 테스트 클래스 | 통과 건수 |
| --- | ---: |
| `NotificationDomainEventListenerTest` | 45 |
| `DirectMessageSseEventIntegrationTest` | 14 |
| `DirectMessageSseIntegrationTest` | 8 |
| `DirectMessageSendServiceIntegrationTest` | 10 |
| `DirectMessageWebSocketIntegrationTest` | 28 |
| **합계** | **105** |

JUnit XML은 `build/test-results/test/`, HTML은 `build/reports/tests/test/index.html`에서 확인합니다. 다음 실행으로 덮어써질 수 있으므로 PR에는 실행 범위와 결과를 함께 남깁니다.

---

[← DM 알림 연동](11-direct-message-integration.md) · [알림 목차](README.md) · [DM 이벤트·SSE 테스트](../conversation/15-direct-message-event-sse.md#검증-구성과-실행)
