# 12. 공유 이력·복구 테스트 읽기

테스트는 저장 계약, 연결별 처리, 실제 Redis·HTTP 수신을 나누어 검증합니다.
아래 표의 책임을 보고 관련 테스트를 선택합니다.

## 테스트별 책임

테스트 클래스는 별도 표시가 없으면 `global.sse` 패키지 아래에 있습니다.

| 테스트 | 검증 범위 |
| --- | --- |
| `SseHistoryPropertiesTest`, `SseRetryPropertiesTest` | 설정값의 허용 범위 |
| `SseHistoryConfigTest`, `SseRetryConfigTest`, `SseEventPublisherConfigTest` (global.config) | 설정·스크립트·발행기 빈 연결, 전용 예약 실행기 |
| `RedisSseHistoryStoreTest` | 실제 Redis의 저장·중복·충돌·건수 제한·스냅샷·후속 조회 계약 |
| `RedisSseHistoryRetentionTest` | 개별 보관기간, 키 TTL, 오래된 이력·매핑 정리와 전체 만료 |
| `HistorySavingSseEventPublisherTest` | 저장 후 발행 순서, 실패 시 전송 차단과 예외 전달 |
| `HistorySavingSseEventPublisherIntegrationTest` | 실제 Redis 이력 저장, 전송 실패 후 이력 보존 |
| `SseRecoveryServiceTest` | 순서·내용·필터·처리 위치·gap 처리와 실패 전달 |
| `SseDeliveryCoordinatorTest` | 연결별 상태, 동시 신호, 중복 합치기, 재시도 예산·간격 |
| `SseRetryExhaustionTest` | 실제 연결 정리 구성에서 재시도 소진과 complete 실패 |
| `SseRetryLifecycleTest` | 실제 상태·저장소 정리, 콜백·서버 종료·늦은 예약 작업 |
| `SseStreamingIntegrationTest` | 실제 HTTP 프레임, 초기 조회 실패의 503 JSON 응답 |
| `NotificationReplayFilterTest` (domain.notification.sse) | 본인에게 남은 알림만 일괄 필터링 |
| `NotificationRepositoryTest` (domain.notification.repository) | 실제 DB 수신자 조건과 알림 ID 조회 |
| `NotificationSseIntegrationTest` (domain.notification) | 저장 커밋부터 Redis 이력·Pub/Sub·실제 HTTP 수신 |

공통 테스트 소스는 [SSE 테스트 디렉터리](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse),
알림 연동은 [알림 테스트 가이드](../notification/02-testing.md)를 참고합니다.

## 실제 수신 시나리오

`NotificationSseIntegrationTest`는 다음 흐름을 확인합니다.

- 삭제된 알림을 제외하되, DB에서 삭제한 기준 알림의 Redis 커서는 복구에 사용합니다.
- 다른 서버로 재연결해 보관 중인 누락 이력을 받습니다.
- 초기 복구 중 도착한 신호와 중복·역순 신호를 이력 순서로 처리합니다.
- 같은 사용자의 두 연결이 각자의 커서로 복구하고 새 알림을 각각 받습니다.
- 한 연결을 종료해도 다른 연결과 공유 이력이 유지됩니다.
- 전체 키 만료 후 빈 조회를 처리해도 연결·emitter를 유지하고 재시도 없이 새 알림을 받습니다.

마지막 만료 사례는 해당 사용자의 두 키 TTL을 200ms로 바꾸어 만료 상태를 만듭니다.
운영 보관기간이 충분한지 검증하는 테스트가 아니며, 실제 Redis 조회 결과는 Spy의 `callRealMethod()`로 관찰합니다.

## 검증 도구와 한계

| 방식 | 읽을 때 주의할 점 |
| --- | --- |
| 예약 Runnable·콜백 직접 실행 | 정리 후 재조회 차단을 검증. 실제 시간 경과나 모든 스레드 순서를 재현하지 않음 |
| 실제 emitter의 timeout 비교 | 동일 객체·설정 유지 확인. HTTP가 정확히 언제 만료되는지 측정하지 않음 |
| Awaitility | 실제 완료 콜백·키 만료 등 비동기 상태를 제한 시간 동안 기다림 |
| 읽기 타임아웃 | 관찰한 구간에 추가 프레임이 없음을 확인. 영구적인 미전달 보장은 아님 |
| 독립 Spring 컨텍스트 A·B | 서로 다른 빈·포트·연결 저장소. 같은 JVM이며 별도 OS 프로세스가 아님 |

조회 목록을 coordinator가 저장하지 않는 구조와 실제 GC가 언제 메모리를 회수하는지는 구분합니다.
취소된 예약을 수동 실행하는 검증도 이미 실행 중인 모든 I/O의 강제 중단을 보장하지 않습니다.

## 실행 방법

Docker를 실행한 상태에서 최근 연결·만료 회귀 검증을 함께 실행합니다.

```bash
./gradlew test --tests '*NotificationSseIntegrationTest' \
  --tests '*SseRetryLifecycleTest'
```

이 두 클래스의 현재 검증은 각각 12건과 5건입니다. 전체 프로젝트 테스트 결과를 뜻하지 않습니다.

이력 저장·보관기간만 확인할 때:

```bash
./gradlew test --tests '*RedisSseHistoryStoreTest' \
  --tests '*RedisSseHistoryRetentionTest' \
  --tests '*HistorySavingSseEventPublisherIntegrationTest'
```

공통 복구·상태·알림 필터 단위 검증:

```bash
./gradlew test --tests '*SseRecoveryServiceTest' \
  --tests '*SseDeliveryCoordinatorTest' \
  --tests '*SseRetryExhaustionTest' \
  --tests '*NotificationReplayFilterTest'
```

Spring Boot 설정을 읽는 테스트는 test 프로필을 사용합니다.
개별 테스트에 지정된 채널·접두사는 유지하며 로컬 운영 데이터로 바꾸지 않습니다.
결과는 `build/reports/tests/test/index.html`에서 확인합니다.

---

[← 운영 설정](11-history-operations.md) · [목차](README.md) · [관련: 기본 테스트 안내](05-testing.md)
