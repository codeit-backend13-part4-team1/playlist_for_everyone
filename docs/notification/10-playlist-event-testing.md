# 10. 플레이리스트 알림 테스트 읽기와 실행

[#157](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/157)의 테스트는 **알림 변환 규칙**과 **실제 업무 커밋 이후의 연동**을 나눠 검증합니다.
이벤트 값·수신자 정책·실패 처리 흐름은 [9번 연동 가이드](09-playlist-event-integration.md)를 먼저 읽습니다.

## 테스트별 책임

| 테스트 | 실제로 검증하는 것 | 대체하거나 포함하지 않는 것 |
| --- | --- | --- |
| [NotificationDomainEventListenerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/event/listener/NotificationDomainEventListenerTest.java) | 수신자·문구·수준, 중복·행위자 제외, 조회·재발행 실패 처리 | 발행자와 수신자 조회는 Mock. 리스너 직접 호출이므로 트랜잭션 실행 없음 |
| [NotificationPlaylistIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/sse/NotificationPlaylistIntegrationTest.java) | 실제 서비스·PostgreSQL·Flyway, 업무 커밋·롤백, 알림 저장·커밋 후 SSE 발행 요청 | SSE 발행자는 Mock. 수신자 조회·알림 저장소는 Spy. Redis·HTTP 수신 검증 없음 |

통합 테스트의 Spy는 기본적으로 실제 서비스를 조회하고 실제 DB에 저장합니다. 실패 사례에서만 예외를 유도합니다.
공통 생성 요청부터 실제 Redis·HTTP 수신까지는 별도의 [공통 전송 테스트](02-testing.md#실제-http-수신-검증-읽기)가 검증합니다.
플레이리스트 업무부터 브라우저 수신까지를 하나의 테스트로 확인한 것은 아닙니다.

## 단위 테스트에서 확인하는 규칙

| 시나리오 | 기대 결과 |
| --- | --- |
| 정상 수신자 조회 | 각 수신자에게 이벤트의 이름·제목을 사용한 INFO 알림 요청 |
| 수신자 없음 | 요청 미발행 |
| 중복 ID | 같은 조회 목록에서 한 수신자에게 한 번만 발행 |
| 행위자 포함 | 생성은 소유자, 콘텐츠 추가는 `actorId` 제외 |
| 소유자 이름이 null·빈 문자열·공백 | 생성 알림의 이름을 `사용자`로 대체 |
| 수신자 조회 실패 | 예외 미전파·발행 없음, 식별자·예외 타입 기록, 이름·제목·원본 메시지 제외 |
| 첫 수신자의 재발행 실패 | 실패한 수신자를 기록하고 다음 수신자에게 계속 발행 |

단위 테스트는 기존 팔로우·권한 변경·구독 사례도 함께 포함합니다. 각 메서드의 한글 `DisplayName`으로 범위를 확인합니다.
조회 실패 테스트도 리스너 메서드를 호출하여 로그와 미발행을 검증합니다.
빈 목록으로 처리된 조회 실패도 식별자·예외 타입 로그가 남는지 함께 확인합니다.

## 실제 트랜잭션 검증 읽기

테스트 전체에 `@Transactional`을 붙이지 않습니다. 커밋·롤백을 관찰하는 사례는 `TransactionTemplate`으로 업무 경계를 제어합니다.
실패 처리 사례는 서비스 자체의 트랜잭션을 사용하며, 알림은 `REQUIRES_NEW`로 저장됩니다.
콘텐츠 추가 사례의 `@WithMockUser`는 기존 메서드 보안을 통과하기 위한 설정이며, 로그인 API나 JWT 검증은 수행하지 않습니다.

| 두 업무에 공통으로 적용한 시나리오 | 기대 결과 |
| --- | --- |
| 실제 생성·콘텐츠 추가 커밋 | 커밋 전에는 알림·SSE 요청 없음, 커밋 후 조회된 수신자들의 알림 저장·SSE 요청 |
| 업무 이벤트 발행 후 관계 추가 | 커밋 전에 추가한 팔로워·구독자도 포함하여 처리 시점 조회 확인 |
| 실제 업무 롤백 | 업무 변경과 알림 없음, 공통 요청·SSE 미발행 |
| 수신자 조회 실패 | 업무 데이터 유지, 알림·SSE 요청 없음, 식별자·예외 타입 기록 |
| 첫 알림 저장 실패 | 업무 데이터 유지, 이후 정상 수신자의 알림만 저장하고 SSE 요청 |

`ApplicationEvents`에는 롤백된 업무의 발행 기록도 남습니다. 공통 요청·DB 알림·SSE 요청 여부를 함께 확인해 소비 여부를 구분합니다.
사용자는 준비 단계에서 먼저 커밋하여 독립 알림 트랜잭션에서도 조회할 수 있게 합니다.

## 첫 저장 실패를 유도하는 방법

`failFirstNotificationSave()`는 저장소 Spy의 첫 `save` 호출에서 `DataIntegrityViolationException`을 던집니다.
이후 호출에는 Spy의 기존 기본 응답을 사용하여 Spring Data 저장소 프록시로 위임하고 실제 저장을 수행합니다.
이는 **저장 경계의 예외에 대한 처리**를 검증하며, 실제 DB 제약 위반을 발생시키는 사례는 아닙니다.

팔로워·구독자의 조회 순서는 보장하지 않습니다. 준비한 첫 사용자라고 먼저 실패한다고 가정하지 않고,
캡처한 저장 호출 순서로 실패·성공 수신자를 구분한 뒤 DB와 SSE 발행 대상이 일치하는지 확인합니다.
실제 PostgreSQL 저장 실패를 유도하는 기존 사례는 [공통 트랜잭션 테스트](02-testing.md#커밋롤백-검증-읽기)에서 설명합니다.

## 실행 방법

프로젝트 루트에서 단위 테스트만 실행합니다. Docker는 필요하지 않습니다.

```bash
./gradlew test --tests '*NotificationDomainEventListenerTest' --no-daemon --console=plain
```

Docker를 실행한 뒤 두 테스트를 함께 확인합니다. 통합 테스트는 PostgreSQL 16 Testcontainers를 사용합니다.
로컬 DB·Redis·`.env.dev`는 필요하지 않으며, 최초 실행에는 이미지 다운로드가 필요할 수 있습니다.

```bash
./gradlew test --tests '*NotificationDomainEventListenerTest' \
  --tests '*NotificationPlaylistIntegrationTest' --no-daemon --console=plain
```

기존 업무·공통 전송까지 포함한 실행 명령은 [전체 알림 테스트 가이드](02-testing.md#실행-방법)에 있습니다.
결과는 `build/reports/tests/test/index.html`과 `build/test-results/test/`에서 확인합니다.
이 문서는 검증 범위와 실행 방법을 설명하며 특정 실행의 성공 건수를 고정된 최신 결과로 기록하지 않습니다.

---

[← 이전: 플레이리스트 생성·콘텐츠 추가 알림](09-playlist-event-integration.md) · [알림 목차](README.md) · [공통 생성·전송 테스트](02-testing.md)
