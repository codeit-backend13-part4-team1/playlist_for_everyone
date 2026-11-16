# DM 테스트와 Swagger 검증

DM 테스트는 7개 클래스입니다. 도메인 규칙, DB·쿼리, 서비스 처리, HTTP 요청의 경계를 나누어 확인합니다.
Docker·프로필과 대화방 테스트 전체 실행 방법은 [공통 테스트 가이드](04-testing.md)를 참고합니다.

## 테스트별 역할

| 테스트 | 확인하는 범위 | 환경·분리한 의존성 |
| --- | --- | --- |
| [DirectMessageTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/entity/DirectMessageTest.java) | 미읽음 생성, 필수 입력·공백 본문, 동일 사용자·비참여자 거절, ID 기준 비교 | 순수 도메인 테스트. DB·HTTP 제외 |
| [DirectMessageRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/repository/DirectMessageRepositoryTest.java) | 감사 시각·미읽음 영속화, FK·NOT NULL, 기존 메시지의 updated_at 마이그레이션 | PostgreSQL·Flyway·실제 JPA |
| [DirectMessageQueryRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/repository/DirectMessageQueryRepositoryTest.java) | 대화방·참여 조건, 양방향 커서·전체 개수, 사용자·프로필 추가 SQL 방지 | PostgreSQL·QueryDSL, Hibernate 통계 |
| [DirectMessageQueryServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/DirectMessageQueryServiceTest.java) | 초과 항목 제거, 마지막 반환 메시지의 커서, 빈 목록·마지막 페이지 | Reader·Mapper·ConversationService는 Mock |
| [DirectMessageReadServiceIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/DirectMessageReadServiceIntegrationTest.java) | 누적 읽음 범위, 반복·동시 요청, 재조회, hasUnread 연계 | PostgreSQL·실제 서비스·트랜잭션. FileStorage는 Mock, HTTP 제외 |
| [DirectMessageControllerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/DirectMessageControllerTest.java) | 입력 검증, 인증 객체 ID 전달, 목록 JSON·읽음 빈 응답, 오류 계약 | Service는 Mock, 보안 필터 비활성화 |
| [DirectMessageSecurityIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/DirectMessageSecurityIntegrationTest.java) | JWT·CSRF, 참여·기준 메시지 검증과 DB 상태, 조회 중 프로필 URL 변환 | PostgreSQL·실제 필터·서비스. UserSessionRepository·FileStorage는 Mock |

현재 대화방 11개와 DM 7개를 합쳐 18개 클래스가 있습니다. 이 숫자는 **작성된 클래스 구성**이며 최신 전체 실행의 통과 건수를 의미하지 않습니다.
그중 DM Repository 2개·읽음 서비스·보안 통합의 4개 클래스가 PostgreSQL을 사용합니다.

## 쿼리와 읽음 테스트에서 확인할 것

| 상황 | 검증 내용 |
| --- | --- |
| 두 참여자의 목록 조회 | 해당 대화방의 송수신 메시지와 전체 개수만 포함 |
| 같은 생성 시각·양방향 페이지 이동 | ID를 함께 비교하고 전체 개수는 커서와 무관하게 유지 |
| 커서 기준 메시지 없음 | 전달받은 시각·ID로 다음 범위를 조회 |
| 결과 수 증가·프로필 접근 | 준비된 데이터에서 SQL 수가 일정하고 사용자·프로필 접근으로 추가 SQL이 발생하지 않음 |
| 누적 읽음·자신이 보낸 기준 메시지 | 기준 이하의 수신 미읽음 메시지만 갱신, 다른 대화방·발신 메시지·기준 이후 메시지 유지 |
| 대상 없음·반복·이전 기준 | 정상 종료, 이미 읽은 메시지의 상태와 수정 시각 유지 |
| 동일·이전 기준의 동시 요청 | 독립 트랜잭션의 실행 순서를 조정하여 먼저 반영된 읽음 상태·수정 시각 보존 확인 |
| 같은 트랜잭션에서 이미 조회한 메시지 | 읽음 처리 후 **재조회**하면 최신 상태 확인. 기존 객체의 자동 갱신을 검증하는 것은 아님 |
| 일부·전체 읽음 | 이후 대화방 조회의 hasUnread가 남은 수신 미읽음 여부와 일치 |

마이그레이션 테스트는 이전 스키마에 메시지를 넣은 뒤 현재 마이그레이션을 적용하여 데이터와 null 수정 시각 보존을 확인합니다.
SQL 수 테스트는 준비된 데이터에서 연관관계 조회 효과를 확인하며, 운영 트래픽의 성능 측정은 포함하지 않습니다.
서비스의 동시 요청 검증 역시 실제 HTTP 동시 요청이나 모든 실행 순서를 검증한 것은 아닙니다.

## 실제 보안 요청의 검증 범위

컨트롤러 테스트는 `addFilters = false`이므로 JWT·CSRF 검증은 보안 통합 테스트에서 확인합니다.
보안 통합 테스트는 실제 JWT를 발급하고 `GET /api/auth/csrf-token`의 쿠키로 CSRF 헤더를 구성합니다.
파라미터화 실행을 포함한 14건의 구성은 다음과 같습니다.

| 상황 | 실행 수 | 확인 내용 |
| --- | --- | --- |
| 두 참여자의 정상 GET | 2 | CSRF 없이 목록 200, 실제 발신자·수신자·프로필, DB 상태 유지 |
| 정상 JWT·CSRF로 읽음 POST | 1 | 200 빈 본문, 해당 수신 메시지의 상태·수정 시각 변경 |
| JWT 없는 GET·POST | 2 | 401, DB 상태 유지. POST에는 정상 CSRF를 준비하여 인증 실패만 확인 |
| CSRF 누락·불일치 | 2 | 유효한 JWT가 있어도 403, DB 상태 유지 |
| 대화방 미존재·비참여 × GET·POST | 4 | 대화방 404, DB 상태 유지 |
| 기준 메시지 미존재·다른 대화방 | 3 | 메시지 404, 다른 대화방의 참여 여부에 관계없이 DB 상태 유지 |

이 테스트는 `open-in-view=false`이며 정상 HTTP 조회의 URL 변환 시 DB 트랜잭션이 비활성인지도 검사합니다.
상위 트랜잭션에서 서비스를 호출하는 경우까지 같은 경계를 보장하는 것은 아닙니다.
UserSessionRepository의 현재 세션 여부와 FileStorage의 URL 반환은 Mock으로 준비합니다.
JWT 필터·CSRF·서비스·DB는 실제 구성이지만 Redis 세션 저장·만료, S3 통신, 로그인 API는 검증하지 않습니다.

## 실행 명령

도메인·조회 서비스·컨트롤러:

```bash
./gradlew test --tests '*DirectMessageTest' --tests '*DirectMessageQueryServiceTest' --tests '*DirectMessageControllerTest'
```

실제 PostgreSQL 영속성·조회·읽음·보안 통합(Docker 필요):

```bash
./gradlew test --tests '*DirectMessageRepositoryTest' --tests '*DirectMessageQueryRepositoryTest' --tests '*DirectMessageReadServiceIntegrationTest' --tests '*DirectMessageSecurityIntegrationTest'
```

DM 7개 클래스 전체:

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.domain.conversation.*.DirectMessage*Test'
```

보고서는 `build/reports/tests/test/index.html`과 `build/test-results/test/`에 생성됩니다.
대화방 생성·조회까지 포함하려면 [전체 실행 명령](04-testing.md#실행-명령)을 사용합니다.

## Swagger 확인 항목

문서 접속·CSRF 전달 방법은 [API와 보안](01-api-security.md#swagger-문서-구성)에 있습니다.

| 확인 대상 | 기대 내용 |
| --- | --- |
| 그룹·인터페이스 | 대화방 그룹에 기존 4개와 DM 2개 API 표시. ConversationApi·DirectMessageApi는 분리 유지 |
| 인증 | Bearer 인증 표시. SecurityUser·requesterId는 일반 입력에 노출되지 않음 |
| DM GET 입력 | conversationId와 쿼리 5개로 총 6개. limit·sortBy·sortDirection 필수, cursor·idAfter 선택 |
| 정렬·커서 | createdAt, ASCENDING/DESCENDING, 커서 두 값의 동시 전달 설명 |
| GET 응답 | 200/400/401/404/500. application/json의 CursorResponseMessageDto와 마지막 페이지 예시 |
| 읽음 POST 입력 | 경로 UUID 2개, 요청 본문 없음, JWT·CSRF 필요 |
| POST 응답 | 200은 content 없는 빈 응답. 오류 400/401/403/404/500은 공통 오류 스키마 |

조회 결과의 sender·receiver는 실제 송수신 방향이며, 마지막 페이지의 커서 두 값은 null입니다.
OpenAPI와 UI 표시 확인은 문서 GET 검증이고, 실제 업무 요청 검증은 위 MockMvc 테스트의 역할입니다.

## 확인된 검증 기록

다음은 2026-11-15에 확인한 기록입니다. 이번 MD 갱신 과정에서 테스트·서버를 새로 실행한 결과는 아닙니다.

| 대상 | 확인된 결과 |
| --- | --- |
| DM HTTP 테스트 | 컨트롤러 20건 + 보안 통합 14건 = 34건 통과. 실패·오류·건너뜀 0건 |
| OpenAPI·Swagger UI | DM 요청·응답, 인증 객체 숨김, 대화방 그룹의 API 6개 통합 표시 확인 |
| 후속 설명 줄바꿈·Java 주석 | 각각 최신 소스로 compileJava 통과. 주석 22개 파일은 실행 코드 동일·diff 검사 통과 |
| 최신 대화방·DM 전체 또는 프로젝트 전체 | 이 기록에서 전체 통과 건수를 확정하지 않음 |

UI 확인 뒤 Swagger 설명의 줄바꿈이 추가되어 컴파일을 다시 확인했습니다. 이후 주석 변경은 실행 코드·Swagger 문자열을 유지했습니다.
기존 #58·#59의 날짜가 있는 결과는 [이전 검증 기록](04-testing.md#기존-검증-기록)에 보존하며, 위 34건과 합산하지 않습니다.
로컬 근거는 `build/reports/openapi/direct-message/`, `build/reports/openapi/conversation-group/`, `build/reports/comment-verification/dm-60/`입니다.
이 파일들은 Git에 포함되지 않고 `clean`으로 삭제될 수 있습니다. 재검증은 실행 명령과 새 보고서를 기준으로 합니다.

---

[← DM 읽음 처리](08-direct-message-read.md) · [목차](README.md) · [팀원 개발 가이드](../DEVELOPMENT.md)
