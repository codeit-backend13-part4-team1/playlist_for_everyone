# 5. 테스트 읽기와 실행

SSE 테스트는 설정 바인딩, 저장소의 실제 동작, 서비스 간 협력, MVC 계약, 실제 HTTP 프레임을 검증합니다.
**어떤 역할을 검증하고 어떤 의존성을 대체했는지**부터 읽으면 테스트를 이해하기 쉽습니다.

## 테스트별 책임

| 테스트 | 검증하는 내용 |
| --- | --- |
| [SsePropertiesTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/config/SsePropertiesTest.java) | 설정 누락·허용 범위 위반 시 바인딩 실패, 정상값·경계값 바인딩 성공 |
| [InMemorySseEmitterRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/repository/impl/InMemorySseEmitterRepositoryTest.java) | 실제 메모리 저장소의 등록·삭제·조회 사본·동시 등록과 삭제 |
| [SseConnectionServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseConnectionServiceTest.java) | 연결 식별자, 콜백 등록, 초기 주석, 실패·종료 시 정리 요청 |
| [SseDeliveryServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryServiceTest.java) | 이벤트 ID·이름과 JSON 구조, 여러 연결 전송, 실패한 연결만 삭제하고 계속 전송 |
| [SseConnectionLifecycleTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseConnectionLifecycleTest.java) | 실제 컨텍스트 종료 이벤트가 연결 정리 메서드를 호출하는지 |
| [SseHeartbeatSchedulerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatSchedulerTest.java) | 스케줄러 메서드가 전송 서비스에 하트비트를 요청하는지 |
| [SseHeartbeatSchedulingTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatSchedulingTest.java) | 설정 간격과 지정한 실행기로 작업이 등록되고, 등록된 작업이 서비스를 호출하는지 |
| [SchedulerConfigTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/config/SchedulerConfigTest.java) | 실제 전용 스레드 실행, 종료 후 작업 거부, 반복 작업 취소 |
| [SseControllerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/controller/SseControllerTest.java) | 인증 사용자, 쿼리 검증, 캐시 금지 응답, ASYNC 종료 시 인증 복원 |
| [SseStreamingIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/controller/SseStreamingIntegrationTest.java) | 실제 HTTP의 초기 주석과 알림·DM 프레임, JSON 변환 실패 후 동일 연결로 다음 정상 이벤트 수신 |

## 설정 바인딩 테스트

`SsePropertiesTest`는 `ApplicationContextRunner`에 `SseConfig`만 등록합니다.
`withPropertyValues`로 설정값을 제공하고, 해당 컨텍스트가 시작되는지와 바인딩된 `Duration` 값을 확인합니다.

| 입력 사례 | 기대 결과 |
| --- | --- |
| 하트비트 간격 누락·0·음수 | 컨텍스트 시작 실패 |
| 연결 제한 시간 누락·0·음수·`500us` | 컨텍스트 시작 실패 |
| 하트비트 `15s`, 연결 제한 시간 `1ms` | 시작 성공, 연결 제한 시간의 최솟값 바인딩 확인 |
| 하트비트 `15s`, 연결 제한 시간 `30m` | 시작 성공, 두 값의 바인딩 확인 |

이 runner는 `application.yaml`이나 테스트 프로필 설정 파일을 자동으로 읽지 않습니다.
따라서 여기서 **누락**은 최종 설정값 자체가 없는 경우입니다. 환경변수를 생략해 YAML 기본값을 사용하는 일반 실행과 구분합니다.
Spring Boot 설정 파일을 읽는 통합 테스트와도 설정을 공급하는 방식이 다릅니다.

## Mock 테스트에서 읽어야 할 부분

서비스 테스트는 저장소 인터페이스를 Mock으로 대체합니다.
실제 저장 결과보다 어떤 사용자·연결로 등록이나 삭제를 요청했는지 확인하며, 저장 결과는 저장소 테스트가 맡습니다.

| 도구·준비 방식 | 사용하는 이유 |
| --- | --- |
| `MockedConstruction<SseEmitter>` | 서비스가 내부에서 생성하는 emitter의 콜백 등록과 전송 호출을 관찰합니다. 해당 테스트 블록이 끝나면 해제합니다. |
| `ArgumentCaptor`로 콜백 캡처 | 등록된 완료·타임아웃·오류 콜백을 직접 실행하여 후속 요청을 확인합니다. |
| `doAnswer`로 초기 전송 실패 재현 | 전송 호출 시점에 등록이 먼저 요청되었는지와 예외 정리를 확인합니다. |
| 실패 연결이 먼저인 `LinkedHashMap` | 첫 전송이 실패해도 다음 연결에 실제로 전송을 시도했는지 확인합니다. |

Mock emitter의 `complete()`는 실제 MVC 완료 콜백을 자동 실행하지 않습니다.
전송 서비스 테스트는 실제 `JsonMapper`를 사용하고, builder에 담긴 JSON 문자열을 파싱하여 원본 데이터와 구조·값을 비교합니다.
emitter는 Mock이므로 실제 HTTP 전송과 MVC 메시지 변환기는 실행하지 않습니다. 최종 응답의 이중 인코딩 여부는 HTTP 통합 테스트에서 확인합니다.

현재 서비스 테스트는 실패한 연결의 삭제와 나머지 연결의 계속 전송을 확인합니다.
로그 수준이나 출력 내용·스택 포함 여부를 검증하는 테스트는 없으므로, 해당 정책은 [로그 기준](02-delivery-heartbeat.md#단절과-오류의-로그-기준)과 구현을 함께 확인합니다.

## 실제 MVC·HTTP 테스트의 범위

`SseControllerTest`는 소켓 없이 MockMvc로 요청 바인딩과 보안 필터를 검증합니다.
일반 계약은 `user(principal)`로 인증을 준비하지만, ASYNC 회귀 테스트는 Bearer 요청으로 JWT 필터를 통과합니다.
`complete()` 후 `asyncDispatch()`를 실행하여 종료 시 인증이 복원되는 경로까지 확인합니다.

`SseStreamingIntegrationTest`는 임의 포트의 실제 Tomcat에 연결합니다.
MVC·보안 필터·서비스·메모리 저장소는 실제로 사용하고, 토큰 검증과 사용자 조회 등의 인증 의존성은 Mock으로 대체합니다.
DB·Redis·주기적 하트비트 스케줄러는 이 테스트 컨텍스트에 포함하지 않습니다.

1. 최초 `:connected`와 뒤의 빈 줄을 읽습니다.
2. 알림 또는 DM 이벤트를 로컬 전송 서비스에 전달합니다.
3. `id`, 명세 문자열 `event`, JSON 구조 `data`, 프레임 끝의 빈 줄을 확인합니다.

별도의 `serializationFailureKeepsConnection()`은 최초 프레임을 받은 뒤 JSON 변환 실패를 재현합니다.
전송 호출에서 예외가 전파되지 않고, 저장소에 기존 emitter가 그대로 남는지 확인합니다.
이어서 정상 이벤트를 보내고 재연결 없이 같은 reader에서 ID·이름·JSON 데이터·끝의 빈 줄을 읽습니다.
중간 줄을 건너뛰지 않아 실패한 이벤트의 불완전한 프레임이 남는 경우도 검출합니다.

SSE는 계속 열리는 응답이므로 EOF까지 읽지 않고 필요한 줄만 읽습니다.
연결·읽기에 제한 시간을 두고, `finally`에서 서버의 연결 정리와 클라이언트 연결 해제를 수행합니다.
이 테스트는 공통 payload로 프레임을 검증하며 실제 알림·DM 업무 이벤트 생성까지 검증하지는 않습니다.

`SseEvent` 생성자의 필수값 null 거부와 `data`의 null 허용은 별도 테스트가 없습니다.

## 동시성과 종료 테스트의 해석

동시 등록·삭제 테스트는 시작 지점을 `CyclicBarrier`로 맞추고 `Future.get`으로 결과를 확인합니다.
모든 스레드 실행 순서를 강제하거나 검증하는 테스트는 아니며, 자세한 보장 범위는 [저장소 문서](03-repository-concurrency.md)를 참고합니다.

하트비트 등록 테스트는 실제 시간을 기다리는 대신 Mock 실행기에 등록된 `Runnable`을 직접 실행합니다.
전용 실행기의 실제 스레드와 종료 후 작업 거부·취소는 `SchedulerConfigTest`에서 별도로 확인합니다.

컨텍스트 종료 테스트의 `Lifecycle.stop` 순서 검증이 실행 중인 모든 하트비트의 선행 종료를 의미하지는 않습니다.
Mock emitter의 종료 요청을 확인한 것과 실제 HTTP 종료 처리가 완료된 것도 구분합니다.

## 실행 방법

프로젝트 루트에서 실행합니다. Spring Boot 설정 파일을 읽는 테스트는 [테스트 기본 설정](../../src/test/resources/application.properties)으로 `test` 프로필을 활성화합니다.
Mock 단위 테스트와 위의 `ApplicationContextRunner` 테스트는 이 설정 파일의 로딩을 전제로 하지 않습니다.

설정값 검증만 확인할 때:

```bash
./gradlew test \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.sse.config.SsePropertiesTest' \
  --no-daemon --console=plain
```

SSE와 전용 스케줄러만 확인할 때:

```bash
./gradlew test \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.sse.*' \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.config.SchedulerConfigTest' \
  --no-daemon --console=plain
```

JWT·보안 설정까지 함께 회귀 검증할 때:

```bash
./gradlew test \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.sse.*' \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.config.SchedulerConfigTest' \
  --tests 'com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtAuthenticationFilterTest' \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfigTest' \
  --no-daemon --console=plain
```

프로젝트 전체 빌드·테스트·커버리지 검증은 Docker를 실행한 상태에서 `./gradlew build --no-daemon --console=plain`으로 수행합니다.
SSE 외의 Testcontainers 기반 테스트가 포함되어 있기 때문입니다.
결과는 `build/reports/tests/test/index.html`, 커버리지는 `build/reports/jacoco/test/html/index.html`에서 확인합니다.
이는 테스트 실행 후 생성되는 파일 경로입니다.

---

[← 이전: 비동기 응답과 인증](04-async-security.md) · [목차](README.md)
