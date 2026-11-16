# 2. 알림 생성·전송 테스트 읽기와 실행

알림 테스트는 **발행할 데이터 → 실제 커밋 경계 → Redis를 거친 HTTP 수신** 순서로 읽습니다.
설계 흐름은 [알림 생성·저장과 SSE 전송](01-creation-and-delivery.md), 공통 SSE 테스트는 [SSE 테스트 가이드](../sse/05-testing.md)를 참고합니다.
생산 코드부터 확인하려면 [팔로우 연동](07-domain-event-integration.md), [권한 변경·구독 연동](08-role-subscription-integration.md),
[플레이리스트 생성·콘텐츠 추가 연동](09-playlist-event-integration.md) 중 해당 가이드를 먼저 읽습니다.

## 테스트별 책임

| 테스트 | 실제로 검증하는 것 | 대체하거나 포함하지 않는 것 |
| --- | --- | --- |
| `NotificationCommandTest` | 제목 길이 경계, 말줄임과 이모지의 서로게이트 쌍 보존 | 생성자 직접 호출. Bean Validation·DB 검증 없음 |
| `NotificationDomainEventListenerTest` | 다섯 업무 이벤트의 수신자·문구·수준, 이름 대체, 중복·행위자 제외, 조회·재발행 실패 처리 | 발행자·수신자 조회는 Mock, 리스너 직접 호출. Spring 트랜잭션 실행 없음 |
| `NotificationServiceTest` | 조회한 User 연결, 수신자 미존재 시 저장·발행 미호출, 저장 결과의 DTO·이벤트 구성 | 사용자 조회 컴포넌트·저장소·이벤트 발행자는 Mock. DB·Auditing·트랜잭션 실행 없음 |
| `NotificationSseEventListenerTest` | ID·수신자·이벤트 이름·DTO 보존, 실패 처리·후속 발행·로그 | 발행자는 Mock, 리스너 직접 호출. AFTER_COMMIT 실행 시점 검증 없음 |
| `NotificationIntegrationTest` | 실제 팔로우·권한 변경·구독과 알림 저장, PostgreSQL·Flyway, 커밋·롤백·실패 격리 | SSE 발행자는 Mock, 공통 요청 리스너는 Spy. Redis·HTTP 수신 검증 없음 |
| `NotificationPlaylistIntegrationTest` | 실제 플레이리스트 생성·콘텐츠 추가, 커밋·롤백, 처리 시점 수신자 조회와 첫 저장 실패 후 처리 | SSE 발행자는 Mock, 수신자 조회·알림 저장소는 Spy. Redis·HTTP 수신 검증 없음 |
| `NotificationSseIntegrationTest` | 실제 JWT·DB·Redis·HTTP, 알림 프레임·서버 간 전달·보관 이력 복구 | 별도 OS 프로세스·로그인 API·부하 검증 없음 |
| `UserAdministrationServiceIntegrationTest` | 실제 권한 변경·계정 잠금 저장, 권한 변경 이벤트의 발행 조건과 커밋 후 수신 | 알림 저장·SSE 발행 결과를 검증하는 테스트는 아님 |
| `PlaylistSubscriptionIntegrationTest` | 실제 구독·취소·동시 요청, 구독 이벤트의 발행 횟수와 값 | 알림 변환 리스너는 Mock. 후속 알림 저장·SSE 전송은 실행하지 않음 |

테스트 코드는 [알림 테스트 패키지](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification)에 있습니다.
각 클래스의 한글 `@DisplayName`으로 개별 시나리오를 확인할 수 있습니다.
생산 도메인의 검증은 [사용자 관리](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/user/service/UserAdministrationServiceIntegrationTest.java)와 [구독 통합 테스트](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/playlist/service/impl/PlaylistSubscriptionIntegrationTest.java)에서 확인합니다.
구독 테스트는 업무 이벤트 생산까지 집중하도록 소비자를 대체하고, 실제 알림 소비는 `NotificationIntegrationTest`에서 확인합니다.

권한 변경 단위 테스트는 `USER → ADMIN`, `ADMIN → USER`의 문구를 각각 확인합니다.
구독은 소유자 수신·이름 대체·자기 알림 제외를 검증하며, 세 업무 모두 재발행 실패가 다음 이벤트 처리를 막지 않는지 확인합니다.
로그 필드와 민감 정보 제외는 팔로우뿐 아니라 플레이리스트 생성·콘텐츠 추가의 조회 실패 사례에서도 확인합니다.
두 신규 이벤트의 수신자별 처리와 저장소 Spy 사용 목적은 [플레이리스트 알림 테스트](10-playlist-event-testing.md)에 정리했습니다.

제목·내용의 필수 값 검증은 `NotificationIntegrationTest`의 실제 서비스 호출에서 확인합니다.
같은 `NotificationServiceTest`의 조회·삭제 사례와 JWT 검증은 [조회·인증 테스트 문서](04-query-and-security-testing.md)에서 다룹니다.
두 트랜잭션이 같은 알림을 읽음 처리하는 과정은 [동시 삭제 검증](05-concurrent-deletion-testing.md)으로 이어집니다.
User 연관관계의 저장·삭제·DTO 변환 검증은 [수신자 연관관계 문서](06-receiver-association.md)에서 함께 확인합니다.

## 커밋·롤백 검증 읽기

`NotificationIntegrationTest`는 테스트 전체에 `@Transactional`을 붙이지 않습니다.
직접 이벤트를 발행하는 사례와 실제 권한 변경·구독 사례는 `TransactionTemplate`으로 커밋·롤백을 제어합니다.
`UserAdministrationService.updateRole`과 `PlaylistService.subscribe`는 이 트랜잭션에 참여합니다.
실제 `FollowService.follow()` 호출은 내부 `FollowSaver`의 `REQUIRES_NEW` 커밋을 사용합니다.

| 시나리오 | 확인하는 결과 |
| --- | --- |
| 업무 커밋 전·후 | 커밋 전에는 알림 없음, 커밋 후에는 생성시각을 가진 알림과 SSE 발행 요청 |
| 업무 롤백 | 업무 변경 복구, 알림 미저장, SSE 미발행 |
| 팔로우 이벤트의 커밋 전·후와 롤백 | 커밋 후에만 공통 요청으로 변환, 롤백 시 알림 미생성 |
| `UserFollowedEvent`를 트랜잭션 밖에서 발행 | 업무 이벤트 리스너 미실행, 공통 요청 없음 |
| `NotificationCreateRequestedEvent`를 트랜잭션 밖에서 발행 | 즉시 처리하여 알림 저장·SSE 발행 요청 |
| `NotificationCreatedEvent`를 트랜잭션 밖에서 발행 | SSE 리스너 미실행 |
| 실제 팔로우 등록 | 관계 저장·커밋 후 대상 사용자 알림 저장, SSE 발행 요청 한 번 |
| 실제 권한 변경 커밋 | 커밋 전에는 알림·SSE 요청 없음, 커밋 후 변경 대상자의 알림 저장·SSE 발행 요청 |
| 실제 구독 커밋 | 커밋 전에는 알림·SSE 요청 없음, 커밋 후 구독 관계와 소유자의 알림 저장·SSE 발행 요청 |
| 실제 권한 변경 롤백 | 기존 권한 복구, 공통 생성 요청·알림·SSE 발행 없음 |
| 실제 구독 롤백 | 구독 정보와 구독자 수 복구, 공통 생성 요청·알림·SSE 발행 없음 |
| 공통 요청 재발행 실패 | 실제 팔로우 유지, 실패 로그, 알림·SSE 발행 없음 |
| 알림 저장 트랜잭션 커밋 전·후 | 커밋 전 미발행, 커밋 후 한 번 발행 |
| 알림 저장 트랜잭션 롤백 | 알림 미저장, 생성 이벤트가 등록되었어도 SSE 미발행 |
| 입력 검증 실패 | ConstraintViolationException 기록, 원래 업무 커밋 유지, 실패 알림 미저장·미발행 |
| 수신자 미존재 | 실제 사용자 조회의 UserNotFoundException 기록, 원래 업무 커밋 유지, 실패 알림 미저장·미발행 |
| 실제 DB 저장 실패 | DataIntegrityViolationException 기록, 원래 업무 커밋 유지, 실패 알림 미저장·미발행 |
| `SsePublishException` 발생 | 업무·알림 데이터 유지, 저장 실패로 오분류하지 않음, 이후 알림은 정상 발행 |

`ApplicationEvents`에는 트랜잭션 안에서 발행한 업무 이벤트가 롤백 후에도 기록됩니다.
발행 기록이 곧 커밋 후 소비의 증거는 아니므로, 롤백 사례는 공통 생성 요청·DB 알림·SSE 발행 요청이 없는지도 확인합니다.

입력 검증·사용자 조회·DB 저장은 서로 다른 실패 지점이므로 각각 확인하고, 실패 뒤 정상 알림의 저장·발행도 검증합니다.
DB 실패 사례는 존재하는 수신자와 정상 본문에 `(char) 0`을 덧붙인 값을 사용합니다.
이는 Java의 `null`이 아닌 **NUL 문자**로, 입력 검증을 통과한 뒤 PostgreSQL 저장이 실패하도록 준비한 값입니다.

업무 이벤트 리스너는 실제 빈입니다. 공통 요청 리스너의 Spy도 기본적으로 실제 동작합니다.
재발행 실패 사례에서만 공통 요청 수신 시 예외를 던지도록 하며, 팔로우 유지와 도메인 리스너의 실패 로그를 함께 확인합니다.

알림 저장 트랜잭션의 커밋 전 상태를 검사하는 사례는 저장소와 생성 이벤트를 테스트 트랜잭션 안에서 직접 사용합니다.
`save` 서비스의 `REQUIRES_NEW`는 반환 시 이미 커밋되므로, 이 방식으로 저장 트랜잭션 내부를 관찰합니다.
별도의 공통 생성 요청 사례가 실제 리스너 → 서비스 경로를 함께 검증합니다.

직접 발행 사례의 사용자 닉네임 변경은 원래 업무의 저장을 대신하는 검증용 동작입니다.
초기 사용자·권한·플레이리스트는 JDBC로 준비해 검증 대상 외의 업무 이벤트가 발생하지 않게 합니다.
두 사용자를 먼저 커밋해 독립 저장 트랜잭션에서도 조회하게 하며, 수신자를 권한 변경 대상·소유자로, 팔로워를 구독자로 재사용합니다.
종료 시 알림·팔로우·구독·플레이리스트를 지운 뒤 사용자를 삭제합니다. 구독은 플레이리스트보다 먼저 삭제합니다.
단위 테스트가 직접 넣은 생성시각과 실제 JPA Auditing이 채운 생성시각의 검증을 구분합니다.

## 실제 HTTP 수신 검증 읽기

`NotificationSseIntegrationTest`는 PostgreSQL 16과 Redis 7.4 Testcontainers, 임의 포트의 실제 HTTP 서버를 사용합니다.
DB에 사용자를 준비하고 실제 `JwtTokenProvider`로 토큰을 발급합니다. JWT 필터·사용자 조회는 실제로 동작하지만 로그인 API는 호출하지 않습니다.

1. Bearer 토큰으로 `/api/sse`에 연결하여 `200`, `text/event-stream`, 최초 `:connected`를 확인합니다.
2. 업무 트랜잭션에서 공통 생성 요청을 발행하고 커밋합니다.
3. 기록된 `NotificationCreatedEvent`의 ID와 실제 SSE `id`를 비교하고, `event:notifications`를 확인합니다.
4. `data`를 JSON으로 읽어 DTO와 비교합니다. 내부 이벤트 객체로 감싸거나 JSON 문자열로 이중 인코딩하면 일치하지 않습니다.

이 테스트는 공통 요청에서 시작합니다. 실제 팔로우·권한 변경·구독부터 SSE 발행 요청까지는 `NotificationIntegrationTest`,
플레이리스트 생성·콘텐츠 추가부터는 `NotificationPlaylistIntegrationTest`가 검증합니다.
각 업무 서비스부터 브라우저 수신까지를 하나의 테스트로 실행한 것은 아닙니다.

#53에서는 삭제된 알림 제외, 삭제된 기준 알림의 커서 유지, 다른 서버 재연결, 복구 중 도착한 신호,
중복·역순 신호, 연결별 커서, 한 연결 종료와 전체 이력 만료 후 새 알림 수신도 검증합니다.
`NotificationReplayFilterTest`는 Mock 저장소로 본인 알림의 필터링, 잘못된 data.id 제외, 유효 ID가 없을 때 DB 미호출을 확인합니다.
`NotificationRepositoryTest`는 실제 DB에서 수신자 조건을 포함한 알림 ID 일괄 조회를 검증합니다.
시나리오별 책임과 최근 연결·만료 테스트의 해석은 [이력 테스트 가이드](../sse/12-history-testing.md)에 모았습니다.

수신자 분리 사례는 두 사용자로 연결하고, 다른 사용자 연결에서 읽기 제한 시간 동안 데이터가 오지 않는지 확인합니다.
하트비트 간격을 `1h`로 늘려 해당 판독에 하트비트가 끼어들지 않도록 합니다.
이 검증은 관찰한 시간 범위의 미전달을 확인하며 모든 장애 상황의 전달 보장을 뜻하지 않습니다.

## 두 서버와 테스트 설정

A·B는 **동일 JVM의 독립 Spring 컨텍스트**이며, 서로 다른 HTTP 포트와 emitter 저장소를 사용합니다.
같은 PostgreSQL·Redis·채널을 공유하고, B에만 수신자 연결을 만든 뒤 A에서 알림을 저장합니다.
A에 연결이 없는 상태에서도 B의 HTTP 응답에 이벤트가 도착하는지를 확인합니다.

테스트 프로필은 SSE 자동 구독을 기본으로 끕니다. 이 클래스는 `subscription-enabled=true`와 전용 채널을 명시합니다.
직접 생성한 B 컨텍스트에는 A의 `DynamicPropertySource`가 자동 적용되지 않으므로 DB·Redis·SSE 설정을 별도로 전달합니다.
두 컨텍스트는 test 프로필의 같은 이력 접두사를 사용합니다.
서버의 연결 정리, 클라이언트 `disconnect`, B 컨텍스트 종료를 모두 수행하여 다음 테스트에 자원을 남기지 않습니다.

별도 프로세스의 인증·연결·하트비트는 [Compose 실행](../sse/07-redis-multi-node.md)과
[연결 확인 절차](../sse/08-redis-verification.md)를 참고합니다.
Redis CLI의 PUBLISH만으로는 현재 이력·DB 필터 경로의 알림 수신을 검증할 수 없습니다.
저장부터 실제 수신까지는 이 통합 테스트를 사용하며, Compose 서버를 대상으로 실행하는 테스트는 아닙니다.

## 실행 방법

프로젝트 루트에서 실행합니다. 단위 테스트에는 Docker가 필요하지 않습니다.

```bash
./gradlew test --tests '*NotificationCommandTest' \
  --tests '*NotificationDomainEventListenerTest' \
  --tests '*NotificationServiceTest' \
  --tests '*NotificationSseEventListenerTest' --no-daemon --console=plain
```

Docker를 실행한 뒤 실제 트랜잭션과 Redis·HTTP 통합 테스트를 확인합니다.
로컬 PostgreSQL·Redis·`.env.dev`는 필요하지 않으며, 최초 실행에는 이미지 다운로드가 필요할 수 있습니다.

```bash
./gradlew test --tests '*NotificationIntegrationTest' \
  --tests '*NotificationPlaylistIntegrationTest' \
  --tests '*NotificationSseIntegrationTest' --no-daemon --console=plain
```

생성 입력·업무 이벤트 변환과 공통 요청부터 실제 전송까지의 검증을 함께 실행하려면 다음을 사용합니다.

```bash
./gradlew test --tests '*NotificationCommandTest' \
  --tests '*NotificationDomainEventListenerTest' \
  --tests '*NotificationServiceTest' \
  --tests '*NotificationSseEventListenerTest' \
  --tests '*NotificationIntegrationTest' \
  --tests '*NotificationPlaylistIntegrationTest' \
  --tests '*NotificationSseIntegrationTest' --no-daemon --console=plain
```

#155의 알림 연동과 기존 팔로우의 격리를 함께 확인하려면 다음을 실행합니다.

```bash
./gradlew test --tests '*NotificationDomainEventListenerTest' \
  --tests '*NotificationIntegrationTest' \
  --tests '*FollowServiceIntegrationTest' \
  --tests '*FollowSecurityIntegrationTest' --no-daemon --console=plain
```

#156의 알림 연동과 기존 권한 변경·구독 동작을 함께 확인하려면 다음을 실행합니다.

```bash
./gradlew test --tests '*NotificationDomainEventListenerTest' \
  --tests '*NotificationIntegrationTest' \
  --tests '*UserAdministrationServiceIntegrationTest' \
  --tests '*PlaylistSubscriptionIntegrationTest' --no-daemon --console=plain
```

#157만 확인하는 실행 명령과 커밋·실패 사례의 해석은 [플레이리스트 알림 테스트](10-playlist-event-testing.md#실행-방법)에 있습니다.

실행 결과는 `build/reports/tests/test/index.html`과 `build/test-results/test/`에서 확인합니다.
여기에는 실행 방법과 검증 범위를 기록하며, 특정 실행의 성공 건수를 고정된 최신 결과로 기록하지 않습니다.
위 명령은 프로젝트 전체 테스트·커버리지 검증을 대체하지 않습니다.

---

[← 이전: 알림 생성·저장과 SSE 전송](01-creation-and-delivery.md) · [알림 목차](README.md) · [다음: 목록 조회·읽음 처리와 JWT 인증 →](03-query-and-security.md)
