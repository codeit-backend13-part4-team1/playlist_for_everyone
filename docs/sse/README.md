# SSE 구현 가이드

SSE는 하나의 HTTP 응답을 열어 두고 서버가 클라이언트에 이벤트를 보내는 방식입니다.
이 가이드는 **연결 → 이력 저장·분산 전송 → 복구 → 종료**를 팀원이 따라 읽도록 구성했습니다.
처음 읽는 팀원은 [전체 흐름](00-overall-flow.md)의 두 그림으로 역할과 순서를 먼저 확인합니다.

기준 범위는 [#49 연결 관리](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/49),
[#50 Redis 전송](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/50),
[#53 공유 이력·복구](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/53)의 현재 코드입니다.
Java 17, Spring Boot 4.0.8, Spring MVC를 사용합니다.

## 먼저 구분할 식별자

| 값 | 역할 |
| --- | --- |
| 사용자 UUID | 이력 소유자와 수신자 |
| 연결 UUID | 같은 사용자의 탭·재연결 구분 |
| 이벤트 UUID | SSE 프레임의 id와 lastEventId 쿼리 |
| 내부 저장 위치 | Redis Stream ID. 연결별 후속 조회 기준 |

알림 DTO의 DB 알림 ID는 이벤트 UUID와 별개입니다.
HTTP 응답을 가진 `SseEmitter`는 해당 서버 메모리에 남고, 이력은 Redis에 공유합니다.

## 진입점 구분

`SseEventPublisher`는 저장 후 서버 간 전송을 요청하는 진입점입니다.
`SseDeliveryService` 직접 호출은 현재 서버의 연결에서 저장된 후속 이력을 읽게 하며,
새 이력을 저장하거나 브로커에 재발행하지 않습니다.

요청 스레드가 연결 종료까지 계속 기다리는 구조는 아닙니다.
MVC가 비동기 응답을 관리하고, 전송 시 같은 HTTP 응답에 데이터를 추가합니다.

## 읽기 순서

| 문서 | 답을 얻을 질문 |
| --- | --- |
| [전체 흐름](00-overall-flow.md) | 연결·알림 발행·복구·종료가 어떻게 이어지나요? |
| [1. 연결 생성과 종료](01-connection-lifecycle.md) | 여러 탭과 콜백은 어떻게 구분하나요? |
| [2. 전송과 하트비트](02-delivery-heartbeat.md) | 클라이언트 프레임과 오류 처리는 어떻게 되나요? |
| [3. 저장소와 동시성](03-repository-concurrency.md) | compute와 조회 사본은 왜 필요한가요? |
| [4. 비동기 응답과 인증](04-async-security.md) | ASYNC 종료 시 인증은 어떻게 유지되나요? |
| [5. 기본 테스트 안내](05-testing.md) | Mock과 실제 HTTP 테스트의 범위는 무엇인가요? |
| [6. Redis 전송](06-redis-transport.md) | 각 서버가 신호를 어떻게 받나요? |
| [7. 다중 서버 실행](07-redis-multi-node.md) | 두 프로세스를 어떻게 실행하나요? |
| [8. 연결·수신 검증](08-redis-verification.md) | 수동 연결 확인과 자동 수신 검증은 어떻게 하나요? |
| [9. 공유 이력 계약](09-shared-history.md) | 중복·보관기간·건수·TTL은 어떻게 처리하나요? |
| [10. 복구와 재시도](10-recovery-and-retry.md) | 상태·순서·필터·실패는 어떻게 조정하나요? |
| [11. 운영 설정](11-history-operations.md) | 어떤 값을 조정하고 어떤 로그를 보나요? |
| [12. 이력·복구 테스트](12-history-testing.md) | 만료·다중 연결·재시도는 무엇으로 검증하나요? |

**전체 흐름 → 9 → 10 → 11 → 12** 순서로 읽고 기존 연결 설명을 참고해도 됩니다.
알림 생성의 트랜잭션 경계는 [알림 가이드](../notification/README.md)에 별도로 정리했습니다.

## 현재 제공하는 범위

다중 연결, 하트비트, Redis 분산 전송, 공유 이력, 보관 범위 내 재연결 복구,
알림 삭제 필터와 연결별 제한된 후속 조회 재시도를 제공합니다.

브로커 발행 성공이 브라우저 수신 확인은 아닙니다. Outbox와 모든 장애 상황의 정확히 한 번 전달은 제공하지 않습니다.
업무 이벤트를 알림으로 바꾸는 책임은 알림 도메인에 있습니다. 팔로우는 [#155 연동 가이드](../notification/07-domain-event-integration.md)를 참고합니다.
나머지 업무 이벤트 연동(#45), DM 본문 연동, Kafka 구현체, 연결 수 제한·모니터링(#107)은 후속 작업입니다.
이력 보관 초기값은 운영 부하를 측정해 조정해야 합니다.

---

[프로젝트 README](../../README.md) · [팀원 개발 가이드](../DEVELOPMENT.md) · [다음: 전체 흐름 →](00-overall-flow.md)
