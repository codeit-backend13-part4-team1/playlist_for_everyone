# 테스트 읽기와 실행

정책을 확인하는 단위 테스트, 실제 DB 제약·트랜잭션 테스트, HTTP 보안 테스트를 구분합니다.
같은 정상 응답을 확인하더라도 실행하는 경계가 다릅니다.
이 페이지는 공통 환경과 생성 테스트를, [조회 테스트와 Swagger 검증](06-query-testing.md)은 조회 전용 검증을 설명합니다.

## 테스트별 역할

전체 코드는 [대화방 테스트 디렉터리](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation)에 있습니다.

| 테스트 | 확인하는 범위 | 분리한 의존성·검증하지 않는 범위 |
| --- | --- | --- |
| [ConversationServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/ConversationServiceTest.java) | 자기 대화, 신규·기존 응답 변환, UNIQUE 분류와 재조회 정책 | Reader·Saver·UserSummaryMapper는 Mock. 실제 DB·트랜잭션 제외 |
| [ConversationReaderTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/impl/ConversationReaderTest.java) | 사용자 쌍 정렬, 요청자 전달, 빈 결과·예외 전달 | Repository는 Mock. 실제 조회 쿼리 제외 |
| [ConversationSaverTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/impl/ConversationSaverTest.java) | 사용자 조회, 엔티티 정렬, 상대방 예외 변환과 저장 위임 | Repository·사용자 조회는 Mock. 실제 커밋 제외 |
| [ConversationControllerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationControllerTest.java) | 요청 검증, 인증 객체의 ID 전달, 200·400·500 본문 계약 | Service는 Mock, 보안 필터 비활성화 |
| [ConversationRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/repository/ConversationRepositoryTest.java) | 실제 쿼리, 최신 메시지·미읽음, 연관관계 조회, UNIQUE·FK·NOT NULL | PostgreSQL 사용. 서비스 정책·HTTP 보안 제외 |
| [ConversationServiceIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/ConversationServiceIntegrationTest.java) | 실제 생성·중복·동시 호출, 커밋·외부 롤백, 충돌 복구·URL 실패 | PostgreSQL 사용. FileStorage는 Mock, Reader는 Spy. HTTP 제외 |
| [ConversationSecurityIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationSecurityIntegrationTest.java) | 생성 JWT·CSRF와 생성·조회 4종의 JWT 누락 거절 | UserSessionRepository는 Mock. 실제 Redis 세션·로그인 API 제외 |

조회 전용 테스트는 QueryService·QueryController·QueryRepository·QuerySecurityIntegration의 4개 클래스입니다.
전체 11개 클래스의 실행 결과를 함께 확인해야 생성 기능에 대한 회귀도 포함할 수 있습니다.

## 단위 테스트와 통합 테스트의 차이

Saver 단위 테스트에서 `save()`를 호출했다고 실제 `REQUIRES_NEW` 커밋을 검증한 것은 아닙니다.
저장 커밋 이후 프로필 이미지 URL을 생성하는지, 외부 트랜잭션 롤백과 URL 실패에도 대화방이 남는지는 서비스 통합 테스트에서 확인합니다.
`FileStorage`는 Mock이므로 실제 S3 통신의 성공 여부는 포함하지 않습니다.

실제 UNIQUE 충돌 복구 테스트는 Reader Spy로 첫 조회 이후 다른 트랜잭션의 저장·커밋을 끼워 넣습니다.
이후 실제 저장 충돌이 발생하고 먼저 커밋된 대화방을 재조회하는지 확인합니다.
별도의 동일·역방향 동시 서비스 호출 테스트는 반환 ID가 같고 DB에 한 행만 남는지 확인합니다.

HTTP 200 계약은 컨트롤러 테스트가 담당합니다.
동시 서비스 호출 검증을 HTTP 동시 요청 테스트로 표현하거나, 모든 동시 실행에서 UNIQUE 예외가 반드시 발생했다고 해석하지 않습니다.

## JWT·CSRF 검증을 읽는 방법

컨트롤러 테스트는 `addFilters = false`이며 SecurityContext에 인증 객체를 준비합니다.
요청자 ID 전달을 확인하기 위한 설정으로, 실제 JWT 검증과 CSRF 차단은 보안 통합 테스트의 역할입니다.

보안 통합 테스트는 `JwtTokenProvider`로 JWT를 발급하고 공통 필터를 통과시킵니다.
CSRF는 실제 `GET /api/auth/csrf-token` 응답의 쿠키를 받아 요청 헤더와 함께 전달합니다.
다음 상황을 검증합니다. JWT 누락은 생성·목록·단건·상대방 조회의 4가지 요청으로 실행하므로 총 **8건**입니다.

| 조건 | 기대 결과 |
| --- | --- |
| 유효한 JWT와 정상 CSRF | 200, 인증 사용자와 상대방의 대화방 저장 |
| JWT 없음: 생성·조회 4종 | 401, 저장 없음. 생성 요청에는 정상 CSRF를 준비하여 인증 실패를 분리 |
| 정상 CSRF, 잘못된 JWT 형식 | 401, 저장 없음 |
| 유효한 JWT, CSRF 없음 | 403, 저장 없음 |
| 유효한 JWT, CSRF 쿠키·헤더 불일치 | 403, 저장 없음 |

`UserSessionRepository.isCurrent()`의 결과는 Mock으로 준비합니다.
JWT 서명·요청 필터와 CSRF 처리는 실제 구성으로 실행하지만, Redis의 세션 보관·만료 동작까지 검증하지 않습니다.
만료·변조 JWT, 계정 잠금 등 공통 인증의 모든 경우를 이 테스트에서 검증하는 것도 아닙니다.
조회의 유효 JWT·참여자 제한·GET의 CSRF 불필요 검증은 [조회 보안 통합 테스트](06-query-testing.md#실제-조회-요청의-검증-범위)를 참고합니다.

## 실행 환경

[테스트 프로필 설정](../../src/test/resources/application.properties)이 `test` 프로필을 활성화합니다.
[테스트 환경 설정](../../src/test/resources/application-test.yaml)의 기본 DB는 H2이지만,
Repository·ServiceIntegration·SecurityIntegration 테스트는 Testcontainers의 PostgreSQL 접속 정보로 덮어씁니다.

- Docker 엔진이 실행되어 있어야 하며 `postgres:16` 컨테이너를 테스트가 생성합니다.
- 생성·조회 Repository, ServiceIntegration, 생성·조회 SecurityIntegration의 5개 클래스가 PostgreSQL을 사용합니다.
- 이 클래스들은 Flyway를 활성화하고 `ddl-auto=validate`로 실제 스키마와 매핑을 확인합니다.
- 개발용 Compose DB를 테스트 대상으로 지정하지 않습니다.
- 기본 테스트 프로필은 SSE 자동 구독과 관리자 초기화를 비활성화합니다.

서비스·보안 통합 테스트는 클래스 전체를 롤백용 트랜잭션으로 감싸지 않습니다.
독립 저장·동시 호출에서 접근할 준비 데이터를 저장하고, 각 테스트 뒤 대화방과 사용자 등 해당 데이터를 정리합니다.

## 실행 명령

프로젝트 루트에서 실행합니다. 생성 정책·Reader·Saver 단위 테스트:

```bash
./gradlew test --tests '*ConversationServiceTest' --tests '*ConversationReaderTest' --tests '*ConversationSaverTest'
```

생성 컨트롤러 및 공통 JWT·CSRF 통합 테스트:

```bash
./gradlew test --tests '*ConversationControllerTest' --tests '*ConversationSecurityIntegrationTest'
```

기존 대화방 응답 조회·제약조건과 생성 서비스 트랜잭션:

```bash
./gradlew test --tests '*ConversationRepositoryTest' --tests '*ConversationServiceIntegrationTest'
```

대화방 테스트 전체:

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.domain.conversation.*'
```

결과는 `build/reports/tests/test/index.html`과 `build/test-results/test/`에서 확인합니다.
조회 전용 실행 명령은 [조회 테스트 가이드](06-query-testing.md#실행-명령)에 있습니다.
일부 테스트로 생성한 JaCoCo 보고서를 프로젝트 전체 커버리지로 해석하지 않습니다.

## 기존 검증 기록

다음은 2026-11-13에 실행·확인한 기록입니다. 테스트 수는 파라미터화 테스트의 각 실행을 포함합니다.
이번 MD 문서 작성 중 테스트를 새로 실행한 결과는 아닙니다.

| 검증 시점·대상 | 결과 |
| --- | --- |
| 대화방 전체 회귀 | 11개 클래스, 139건 통과. 실패·오류·건너뜀 0건 |
| 조회 전용 클래스 | 위 전체 실행에 포함된 4개 클래스, 66건 통과 |
| 생성·공통 클래스 | 나머지 7개 클래스, 73건 통과. JWT 누락 조회 요청도 포함 |
| 조회 Swagger 반영 후 HTTP 검증 | 컨트롤러 21건 + 보안 통합 11건 통과 |
| OpenAPI·Swagger UI | 조회 3종의 파라미터·인증 숨김·응답 코드·JSON 스키마·메시지 null 허용 확인 |
| 최신 Swagger 설명 및 Java 주석 | 각각 후속 `compileJava` 통과. 주석 작업은 실행 코드 동일·`git diff --check` 통과 |
| 프로젝트 전체 회귀 테스트 | 전체 회귀 검증 완료 기록 없음 |

전체 회귀 실행 중 Swagger 설명의 줄바꿈 수정이 있었으며, 테스트 통과 뒤 최신 소스로 컴파일을 추가 확인했습니다.
UI 확인은 별도 검증 서버의 문서 GET·화면 표시 검증이고, 실제 업무 API 요청은 테스트 환경의 MockMvc로 검증했습니다.
개발 DB에서 실제 생성 POST까지 검증했다는 의미는 아닙니다.

당시 로컬 기록은 `build/reports/conversation-regression/summary.json`,
`build/reports/openapi/conversation-query/`, `build/reports/conversation-comments/verification.json`에 있습니다.
이 파일들은 Git에 포함되지 않으며 `clean`으로 삭제될 수 있습니다. 재검증은 위 명령과 새 보고서를 기준으로 합니다.

---

[← 검색과 커서 페이지 처리](05-search-pagination.md) · [목차](README.md) · [다음: 조회 테스트와 Swagger 검증 →](06-query-testing.md)
