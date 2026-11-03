# SSE 구현 가이드

SSE(Server-Sent Events)는 하나의 HTTP 응답을 열어 두고 서버가 클라이언트로 이벤트를 보내는 방식입니다.
이 가이드는 모두의 플리에서 연결을 만들고, Redis를 거쳐 이벤트를 전달하고, 연결을 정리하는 공통 구조를 설명합니다.

**기준 범위:** [#49 연결 관리 및 공통 전송 계약](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/49), [#50 Redis Pub/Sub 분산 전송](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/50)의 현재 구현입니다.
Java 17, Spring Boot 4.0.8, Spring MVC 기반이며, 코드 안의 상세 주석과 함께 읽을 수 있습니다.

## 먼저 이해할 세 가지

| 개념 | 이 프로젝트에서의 의미 |
| --- | --- |
| 사용자 ID | 이벤트를 받을 사람을 식별합니다. |
| 연결 ID | 같은 사용자의 탭·재연결을 각각 구분합니다. 연결마다 `SseEmitter`가 하나 있습니다. |
| 이벤트 ID | 전달하는 메시지를 식별합니다. 같은 메시지를 여러 탭에 보내도 동일한 ID를 사용합니다. |

`SseEmitter`는 현재 서버의 HTTP 응답에 데이터를 쓰는 객체입니다.
사용자가 두 탭을 열면 사용자 ID는 같지만 연결 ID와 emitter는 각각 두 개입니다.

## 전체 흐름

```text
연결: 클라이언트 → JWT 인증 → 컨트롤러 → 연결 서비스 → 메모리 저장소
분산 전송: 발행자 → Redis 채널 → 각 서버의 수신자 → 로컬 전송 서비스
로컬 전송: 수신자 연결 조회 → data를 JSON으로 한 번 변환 → 각 emitter → 클라이언트
정리: 완료·타임아웃·오류 콜백 또는 서버 종료 → 해당 연결 제거
```

연결을 맺은 요청 스레드가 응답이 끝날 때까지 계속 대기하는 구조는 아닙니다.
Spring MVC가 비동기 응답을 관리하고, 이후 전송 서비스가 같은 응답에 데이터를 추가합니다.

## 추천 읽기 순서

| 순서 | 문서 | 답을 얻을 수 있는 질문 |
| --- | --- | --- |
| 1 | [연결 생성과 종료](01-connection-lifecycle.md) | 콜백은 언제 실행되고, 여러 탭은 어떻게 구분하나요? |
| 2 | [이벤트 전송과 하트비트](02-delivery-heartbeat.md) | 어떤 데이터를 보내며, 설정값과 오류 로그는 어떻게 확인하나요? |
| 3 | [저장소와 동시성](03-repository-concurrency.md) | 왜 `compute`와 Map 복사가 필요한가요? |
| 4 | [비동기 응답과 인증](04-async-security.md) | 연결을 종료할 때도 인증이 필요한 이유는 무엇인가요? |
| 5 | [테스트 읽기와 실행](05-testing.md) | 설정 검증·Mock·실제 HTTP 테스트는 무엇을 보장하나요? |
| 6 | [Redis 전송 구조와 설정](06-redis-transport.md) | 이벤트가 다른 서버로 어떻게 전달되고, 실패하면 어떻게 처리하나요? |
| 7 | [다중 서버 실행](07-redis-multi-node.md) | 두 서버가 같은 DB·Redis를 사용하도록 어떻게 실행하나요? |
| 8 | [실제 수신 검증](08-redis-verification.md) | 같은 사용자의 두 연결에서 이벤트 수신을 어떻게 확인하나요? |

관련 문서: [알림 구현 가이드](../notification/README.md)에서 #51의 저장·커밋 후 발행 흐름과 실제 수신 테스트를 설명합니다.

설정은 [연결·하트비트](02-delivery-heartbeat.md#설정값과-허용-범위)와 [Redis 전송·구독](06-redis-transport.md#설정과-구독-생명주기)으로 나누어 확인합니다.
오류 로그도 [HTTP 연결 전송](02-delivery-heartbeat.md#단절과-오류의-로그-기준)과 [Redis 발행·수신](06-redis-transport.md#실패-처리와-로그)의 처리 단계에 맞춰 읽습니다.

## 현재 구현과 후속 작업

| 현재 제공하는 기능 | 후속 구현이 필요한 기능 |
| --- | --- |
| 인증된 사용자의 다중 연결, 로컬 전송, 하트비트, 종료 정리 | 동시 연결 수 제한과 연결 수 모니터링([#107](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/107)) |
| Redis Pub/Sub을 통한 서버 간 전달, 구독 연결 복구 후 새 이벤트 수신 | Kafka 구현체 추가 |
| `SseEventPublisher`를 통한 알림 저장 커밋 후 발행, 실제 Redis·HTTP 수신 검증 | 업무별 알림 생성 연동([#45](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/45)), DM 본문 전송 연동 |
| `lastEventId` 쿼리 입력 및 UUID 형식 검증 | 공유 이력 저장과 누락 이벤트 복구([#53](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/53)) |

`SseDeliveryService` 직접 호출은 해당 서버의 연결에 전달합니다. 서버 간 전달에는 `SseEventPublisher`를 사용합니다.
이벤트 이력 저장이나 누락 재전송은 수행하지 않습니다. 공통 알림 생성 요청부터 실제 수신까지의 연동과,
개별 업무 이벤트를 알림 요청으로 바꾸는 작업·DM 연동은 구분합니다.

---

[프로젝트 README](../../README.md) · [팀원 개발 가이드](../DEVELOPMENT.md) · [다음: 연결 생성과 종료 →](01-connection-lifecycle.md)
