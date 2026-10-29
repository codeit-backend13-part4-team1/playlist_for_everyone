# 5. 테스트 읽기와 실행

SSE 테스트는 저장소의 실제 동작, 서비스 간 협력, MVC 계약, 실제 HTTP 프레임을 나누어 검증합니다.
**어떤 역할을 검증하고 어떤 의존성을 대체했는지**부터 읽으면 테스트를 이해하기 쉽습니다.

## 테스트별 책임

| 테스트 | 검증하는 내용 |
| --- | --- |
| [InMemorySseEmitterRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/repository/impl/InMemorySseEmitterRepositoryTest.java) | 실제 메모리 저장소의 등록·삭제·조회 사본·동시 등록과 삭제 |
| [SseConnectionServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseConnectionServiceTest.java) | 연결 식별자, 콜백 등록, 초기 주석, 실패·종료 시 정리 요청 |
| [SseDeliveryServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseDeliveryServiceTest.java) | 이벤트 필드, 여러 연결 전송, 실패한 연결만 삭제하고 계속 전송 |
| [SseConnectionLifecycleTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/service/SseConnectionLifecycleTest.java) | 실제 컨텍스트 종료 이벤트가 연결 정리 메서드를 호출하는지 |
| [SseHeartbeatSchedulerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatSchedulerTest.java) | 스케줄러 메서드가 전송 서비스에 하트비트를 요청하는지 |
| [SseHeartbeatSchedulingTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/scheduler/SseHeartbeatSchedulingTest.java) | 설정 간격과 지정한 실행기로 작업이 등록되고, 등록된 작업이 서비스를 호출하는지 |
| [SchedulerConfigTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/config/SchedulerConfigTest.java) | 실제 전용 스레드 실행, 종료 후 작업 거부, 반복 작업 취소 |
| [SseControllerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/controller/SseControllerTest.java) | 인증 사용자, 쿼리 검증, 캐시 금지 응답, ASYNC 종료 시 인증 복원 |
| [SseStreamingIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/controller/SseStreamingIntegrationTest.java) | 실제 HTTP의 초기 주석과 알림·DM 프레임 이름·ID·JSON 데이터 |

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
또한 `send()`에 전달된 builder를 검사하는 테스트만으로 실제 JSON 직렬화 결과까지 확인할 수는 없습니다.
이를 보완하는 검증이 MVC·실제 HTTP 테스트입니다.

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

SSE는 계속 열리는 응답이므로 EOF까지 읽지 않고 필요한 줄만 읽습니다.
연결·읽기에 제한 시간을 두고, `finally`에서 서버의 연결 정리와 클라이언트 연결 해제를 수행합니다.
이 테스트는 공통 payload로 프레임을 검증하며 실제 알림·DM 업무 이벤트 생성까지 검증하지는 않습니다.

## 동시성과 종료 테스트의 해석

동시 등록·삭제 테스트는 시작 지점을 `CyclicBarrier`로 맞추고 `Future.get`으로 결과를 확인합니다.
모든 스레드 실행 순서를 강제하거나 검증하는 테스트는 아니며, 자세한 보장 범위는 [저장소 문서](03-repository-concurrency.md)를 참고합니다.

하트비트 등록 테스트는 실제 시간을 기다리는 대신 Mock 실행기에 등록된 `Runnable`을 직접 실행합니다.
전용 실행기의 실제 스레드와 종료 후 작업 거부·취소는 `SchedulerConfigTest`에서 별도로 확인합니다.

컨텍스트 종료 테스트의 `Lifecycle.stop` 순서 검증이 실행 중인 모든 하트비트의 선행 종료를 의미하지는 않습니다.
Mock emitter의 종료 요청을 확인한 것과 실제 HTTP 종료 처리가 완료된 것도 구분합니다.

## 실행 방법

프로젝트 루트에서 실행합니다. [테스트 기본 설정](../../src/test/resources/application.properties)이 `test` 프로필을 활성화합니다.

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
