# 5. 테스트 읽기와 실행

먼저 어떤 역할을 검증하고 어떤 의존성을 대체했는지 확인합니다.
공유 이력·복구·재시도의 상세 검증은 [12. 이력 테스트](12-history-testing.md)에 모았습니다.

## 테스트별 책임

| 테스트 | 현재 검증 대상 |
| --- | --- |
| SsePropertiesTest | 연결·하트비트 설정값의 정상값·경계값·실패 |
| InMemorySseEmitterRepositoryTest | 실제 저장소 등록·삭제·조회 사본·동시 변경 |
| SseConnectionServiceTest | 연결 ID, 콜백, 상태·저장소 등록, 초기 복구와 실패 정리 |
| SseDeliveryServiceTest | 수신자별 연결의 coordinator 호출, 실패한 연결 정리, 하트비트 |
| SseConnectionLifecycleTest | 컨텍스트 종료 이벤트의 연결 정리 호출 |
| SseHeartbeatSchedulerTest / SseHeartbeatSchedulingTest | 하트비트 요청과 지정 실행기 등록 |
| SchedulerConfigTest | 실제 하트비트 실행기 스레드·종료·취소 |
| SseControllerTest | 인증 사용자·쿼리·응답·ASYNC 종료 계약 |
| SseStreamingIntegrationTest | 실제 HTTP 프레임과 초기 조회 실패의 503 응답 |

소스: [SSE 테스트](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse),
[공통 설정 테스트](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/config).

## 설정 바인딩 테스트

ApplicationContextRunner는 필요한 설정 클래스만 등록하고 withPropertyValues로 값을 제공합니다.
이 runner가 application.yaml과 테스트 프로필을 자동으로 읽는 것은 아닙니다.
따라서 설정 누락 검증은 최종 값이 없는 경우이며, 환경변수를 생략해 YAML 기본값을 쓰는 실행과 다릅니다.

| 설정 | 검증 방식 |
| --- | --- |
| 연결·하트비트 | SseConfig와 SseProperties의 값 바인딩 |
| 전송 방식·채널 | SseTransportConfig와 전송 설정의 유효성 |
| 이력·재시도 | SseHistoryConfig·SseRetryConfig의 값과 이름 있는 빈 |
| 발행 구현체 선택 | SseEventPublisherConfigTest의 기본 주입·delegate 연결 |

## Mock과 실제 HTTP의 차이

| 방식 | 해석 |
| --- | --- |
| MockedConstruction으로 emitter 관찰 | 서비스 내부에서 생성한 객체의 콜백·호출을 확인 |
| ArgumentCaptor로 콜백 실행 | 등록된 정리 동작을 확인. MVC가 그 시각에 실제 종료했다는 뜻은 아님 |
| Mock coordinator·저장소 | 해당 계층의 요청 계약 검증. 실제 Redis·상태 처리는 별도 테스트 |
| 실제 메모리 저장소·coordinator | 연결별 상태와 취소된 예약의 늦은 실행 검증 |
| 실제 HTTP reader | 프레임 id·event·data와 스트림 종료 확인 |

SseStreamingIntegrationTest의 인증 의존성 대체와 NotificationSseIntegrationTest의 실제 JWT 검증은 구분합니다.
전자의 JSON 변환 실패 사례도 현재는 공통 발행 단계의 실패 뒤 기존 연결로 다음 정상 이벤트를 받는 검증입니다.
실패한 이벤트가 이미 브라우저로 일부 전송되었다고 해석하지 않습니다.

## Redis 테스트의 범위

| 테스트 | 실제로 사용하는 부분 |
| --- | --- |
| RedisSsePubSubIntegrationTest | 실제 Redis·독립 컨텍스트, 로컬 전송 서비스는 Mock |
| RedisSseDeliveryIntegrationTest | 실제 Redis 발행·수신·이력 기반 로컬 전달, emitter는 Mock |
| HistorySavingSseEventPublisherIntegrationTest | 실제 Redis 저장과 전송 실패 후 이력 보존 |
| NotificationSseIntegrationTest | 실제 PostgreSQL·Redis·JWT·HTTP 수신 |

Pub/Sub 연결을 끊었다가 재구독하는 테스트는 새 신호 수신을 확인합니다.
보관 이력의 복구 보장은 별도의 [이력·복구 테스트](12-history-testing.md)에서 확인합니다.
독립 Spring 컨텍스트는 같은 JVM의 두 서버 구성이며 별도 OS 프로세스는 아닙니다.

test 프로필은 자동 구독을 끄고 테스트용 이력 접두사를 사용합니다.
Redis SSE 통합 테스트는 구독을 켜고 필요하면 클래스별 채널·접두사를 지정합니다.
이 설정이 다른 기능의 Redis 의존성까지 없애지는 않습니다.

## 실행 방법과 검증 한계

Docker가 필요한 통합 테스트를 실행하기 전에 Docker 엔진을 켭니다.

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.global.sse.*' \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.config.Sse*Test' \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.config.SchedulerConfigTest'
```

이 패키지 필터에는 알림 도메인이 포함되지 않습니다.
알림 저장·수신은 [알림 실행 명령](../notification/02-testing.md#실행-방법),
최근 연결·만료 검증은 [이력 테스트 실행 명령](12-history-testing.md#실행-방법)을 사용합니다.
결과는 build/reports/tests/test/index.html에서 확인합니다.

동시성 테스트는 지정한 경합 상황을 재현하며 모든 가능한 실행 순서를 검증하지 않습니다.
Mock 예약 작업을 직접 실행한 결과를 실제 대기시간 측정으로 해석하지 않습니다.
ContextClosedEvent의 정리 검증도 실행 중인 모든 하트비트·I/O가 먼저 끝났다는 보장은 아닙니다.
실제 부하·메모리 조정은 [운영 가이드](11-history-operations.md)에서 구분합니다.

---

[← 비동기 인증](04-async-security.md) · [목차](README.md) · [다음: Redis 전송 →](06-redis-transport.md)
