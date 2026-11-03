# 2. 알림 전송 테스트 읽기와 실행

알림 테스트는 **발행할 데이터 → 실제 커밋 경계 → Redis를 거친 HTTP 수신** 순서로 읽습니다.
설계 흐름은 [알림 생성·저장과 SSE 전송](01-creation-and-delivery.md), 공통 SSE 테스트는 [SSE 테스트 가이드](../sse/05-testing.md)를 참고합니다.

## 테스트별 책임

| 테스트 | 실제로 검증하는 것 | 대체하거나 포함하지 않는 것 |
| --- | --- | --- |
| `NotificationServiceTest` | 저장소 결과를 DTO로 복사하고 생성 이벤트 발행 요청 | 저장소·이벤트 발행자는 Mock. DB·Auditing·트랜잭션 실행 없음 |
| `NotificationSseEventListenerTest` | ID·수신자·이벤트 이름·DTO 보존, 실패 처리·후속 발행·로그 | 발행자는 Mock, 리스너 직접 호출. AFTER_COMMIT 실행 시점 검증 없음 |
| `NotificationIntegrationTest` | PostgreSQL·Flyway, 실제 커밋·롤백, 발행 요청과 실패 격리 | `SseEventPublisher`만 Mock으로 교체. Redis·HTTP 수신 검증 없음 |
| `NotificationSseIntegrationTest` | 실제 JWT·DB·Redis·HTTP, 알림 프레임과 서버 간 전달 | 별도 OS 프로세스·로그인 API·누락 복구 검증 없음 |

테스트 코드는 [알림 테스트 패키지](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification)에 있습니다.
각 클래스의 한글 `@DisplayName`으로 개별 시나리오를 확인할 수 있습니다.

## 커밋·롤백 검증 읽기

`NotificationIntegrationTest`는 테스트 전체에 `@Transactional`을 붙이지 않습니다.
기본 롤백으로 끝나는 테스트에서는 `AFTER_COMMIT` 경로를 관찰할 수 없으므로 `TransactionTemplate`으로 종료 시점을 직접 만듭니다.

| 시나리오 | 확인하는 결과 |
| --- | --- |
| 업무 커밋 전·후 | 커밋 전에는 알림 없음, 커밋 후에는 생성시각을 가진 알림과 SSE 발행 요청 |
| 업무 롤백 | 업무 변경 복구, 알림 미저장, SSE 미발행 |
| 생성 요청·생성 이벤트를 트랜잭션 밖에서 발행 | 각 AFTER_COMMIT 리스너 미실행 |
| 알림 저장 트랜잭션 커밋 전·후 | 커밋 전 미발행, 커밋 후 한 번 발행 |
| 알림 저장 트랜잭션 롤백 | 알림 미저장, 생성 이벤트가 등록되었어도 SSE 미발행 |
| 알림 검증·저장 실패 | 원래 업무 커밋 유지, 이후 정상 알림은 저장·발행 |
| `SsePublishException` 발생 | 업무·알림 데이터 유지, 저장 실패로 오분류하지 않음, 이후 알림은 정상 발행 |

커밋 전 상태를 검사하는 사례는 저장소와 생성 이벤트를 테스트 트랜잭션 안에서 직접 사용합니다.
`save` 서비스의 `REQUIRES_NEW`는 반환 시 이미 커밋되므로, 이 방식으로 저장 트랜잭션 내부를 관찰합니다.
별도의 공통 생성 요청 사례가 실제 리스너 → 서비스 경로를 함께 검증합니다.

사용자 닉네임 변경은 원래 업무의 저장을 대신하는 검증용 동작입니다.
수신자는 새 알림 트랜잭션에서도 참조할 수 있게 먼저 저장하고, 커밋된 테스트 데이터는 실행 후 직접 삭제합니다.
단위 테스트가 직접 넣은 생성시각과 실제 JPA Auditing이 채운 생성시각의 검증을 구분합니다.

## 실제 HTTP 수신 검증 읽기

`NotificationSseIntegrationTest`는 PostgreSQL 16과 Redis 7.4 Testcontainers, 임의 포트의 실제 HTTP 서버를 사용합니다.
DB에 사용자를 준비하고 실제 `JwtTokenProvider`로 토큰을 발급합니다. JWT 필터·사용자 조회는 실제로 동작하지만 로그인 API는 호출하지 않습니다.

1. Bearer 토큰으로 `/api/sse`에 연결하여 `200`, `text/event-stream`, 최초 `:connected`를 확인합니다.
2. 업무 트랜잭션에서 공통 생성 요청을 발행하고 커밋합니다.
3. 기록된 `NotificationCreatedEvent`의 ID와 실제 SSE `id`를 비교하고, `event:notifications`를 확인합니다.
4. `data`를 JSON으로 읽어 DTO와 비교합니다. 내부 이벤트 객체로 감싸거나 JSON 문자열로 이중 인코딩하면 일치하지 않습니다.

수신자 분리 사례는 두 사용자로 연결하고, 다른 사용자 연결에서 읽기 제한 시간 동안 데이터가 오지 않는지 확인합니다.
하트비트 간격을 `1h`로 늘려 해당 판독에 하트비트가 끼어들지 않도록 합니다.
이 검증은 관찰한 시간 범위의 미전달을 확인하며 모든 장애 상황의 전달 보장을 뜻하지 않습니다.

## 두 서버와 테스트 설정

A·B는 **동일 JVM의 독립 Spring 컨텍스트**이며, 서로 다른 HTTP 포트와 emitter 저장소를 사용합니다.
같은 PostgreSQL·Redis·채널을 공유하고, B에만 수신자 연결을 만든 뒤 A에서 알림을 저장합니다.
A에 연결이 없는 상태에서도 B의 HTTP 응답에 이벤트가 도착하는지를 확인합니다.

테스트 프로필은 SSE 자동 구독을 기본으로 끕니다. 이 클래스는 `subscription-enabled=true`와 전용 채널을 명시합니다.
직접 생성한 B 컨텍스트에는 A의 `DynamicPropertySource`가 자동 적용되지 않으므로 DB·Redis·SSE 설정을 별도로 전달합니다.
서버의 연결 정리, 클라이언트 `disconnect`, B 컨텍스트 종료를 모두 수행하여 다음 테스트에 자원을 남기지 않습니다.

별도 프로세스의 수동 검증은 [Compose 실행](../sse/07-redis-multi-node.md)과 [Redis CLI 수신 확인](../sse/08-redis-verification.md)을 참고합니다.
CLI 절차는 공통 전송 검증이며 알림 DB 저장이나 생성 리스너를 실행하지 않습니다.

## 실행 방법

프로젝트 루트에서 실행합니다. 단위 테스트에는 Docker가 필요하지 않습니다.

```bash
./gradlew test --tests '*NotificationServiceTest' \
  --tests '*NotificationSseEventListenerTest' --no-daemon --console=plain
```

Docker를 실행한 뒤 실제 트랜잭션과 Redis·HTTP 통합 테스트를 확인합니다.
로컬 PostgreSQL·Redis·`.env.dev`는 필요하지 않으며, 최초 실행에는 이미지 다운로드가 필요할 수 있습니다.

```bash
./gradlew test --tests '*NotificationIntegrationTest' \
  --tests '*NotificationSseIntegrationTest' --no-daemon --console=plain
```

이번 연동과 관련된 네 클래스를 함께 확인하려면 다음을 사용합니다.

```bash
./gradlew test --tests '*NotificationServiceTest' \
  --tests '*NotificationSseEventListenerTest' \
  --tests '*NotificationIntegrationTest' \
  --tests '*NotificationSseIntegrationTest' --no-daemon --console=plain
```

실행 결과는 `build/reports/tests/test/index.html`과 `build/test-results/test/`에서 확인합니다.
여기에는 실행 방법과 검증 범위를 기록하며, 특정 실행의 성공 건수를 고정된 최신 결과로 기록하지 않습니다.
위 명령은 프로젝트 전체 테스트·커버리지 검증을 대체하지 않습니다.

---

[← 이전: 알림 생성·저장과 SSE 전송](01-creation-and-delivery.md) · [알림 목차](README.md) · [팀원 개발 가이드](../DEVELOPMENT.md)
