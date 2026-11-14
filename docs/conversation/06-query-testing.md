# 대화방 조회 테스트와 Swagger 검증

대화방 조회 전용 테스트는 4개 클래스입니다. 서비스 정책, HTTP 입력, 실제 쿼리, 인증을 거친 요청을 나누어 검증합니다.
Docker·프로필과 전체 실행 기록은 [테스트 읽기와 실행](04-testing.md)을 참고합니다.
메시지 목록·읽음 처리의 7개 클래스와 API 6개 통합 표시는 [DM 테스트와 Swagger 검증](09-direct-message-testing.md)에서 설명합니다.

## 테스트별 역할

| 테스트 | 검증하는 내용 | 분리한 의존성 |
| --- | --- | --- |
| [ConversationQueryServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/ConversationQueryServiceTest.java) | 기존 대화방 반환·미존재·자기 대화, 저장 호출 없음, 초과 항목 제거와 다음 커서·마지막 페이지 | Reader·Saver·Mapper를 Mock으로 준비. 실제 DB·URL 변환 제외 |
| [ConversationQueryControllerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationQueryControllerTest.java) | 인증 사용자 ID 전달, 200 응답, 필수 값·형식·커서 조합의 400과 서비스 미호출 | Service를 Mock으로 준비, 보안 필터 비활성화 |
| [ConversationQueryRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/repository/ConversationQueryRepositoryTest.java) | 참여자 제한·검색·정렬·커서·집계, 최신 메시지·미읽음, 추가 SQL 여부 | 실제 PostgreSQL·Querydsl. 서비스·HTTP 제외 |
| [ConversationQuerySecurityIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationQuerySecurityIntegrationTest.java) | 실제 JWT 필터·서비스·DB·Mapper를 연결한 조회와 접근 제한 | UserSessionRepository·FileStorage를 Mock으로 준비 |

컨트롤러 테스트는 SecurityContext에 인증 객체를 준비하여 ID 전달을 확인합니다.
이 테스트의 200은 실제 JWT·세션 검증까지 통과했다는 뜻이 아닙니다.
반대로 보안 통합 테스트는 실제 토큰을 발급해 요청 필터를 통과시키지만 Redis 세션·S3 통신은 검증하지 않습니다.

## 서비스와 Repository에서 나누어 확인할 것

서비스 테스트는 Reader의 결과를 바탕으로 응답을 구성하는 정책을 확인합니다.
`limit + 1` 조회 자체와 검색 SQL의 정확성은 실제 Repository 테스트에서 확인합니다.

| 상황 | 담당 검증 |
| --- | --- |
| 조회 결과 없음·자기 대화 | 서비스의 예외 결정과 Saver 미호출 |
| limit보다 많은 결과 | 초과 항목 제거와 마지막 반환 항목의 다음 커서 |
| 결과 0건·limit 미만·limit와 같음 | 빈 목록 또는 마지막 페이지, 커서 두 값 null |
| 검색어 유무 | 목록·전체 개수 모두 본인 참여 조건 적용 |
| 상대방 이름·과거 메시지 검색 | 공백·대소문자·특수문자 처리, 중복 대화방 없음, 실제 최신 메시지 유지 |
| 최신 메시지와 미읽음 | 생성 시각·ID로 최신 선택, 이전 수신 메시지의 미읽음도 반영, 메시지 없는 대화방 보존 |
| 양방향 커서·동일 시각·기준 행 없음 | 시각·ID 비교로 다음 항목 조회, 전체 개수는 커서와 무관 |
| 결과 수 증가·프로필 접근 | 준비된 데이터에서 조회 결과 2개와 10개의 SQL 수가 같고, 프로필 접근으로 SQL이 추가되지 않음 |

SQL 수 검증은 최신 메시지의 발신자·수신자가 대화방의 두 참여자인 데이터에서 수행합니다.
사용자·프로필을 미리 조회한 효과를 확인하는 테스트이며, 모든 데이터 형태나 운영 부하를 검증한 것은 아닙니다.

## 실제 조회 요청의 검증 범위

보안 통합 테스트는 `open-in-view=false`로 실행하며 다음 **11건**을 확인합니다.

| 상황 | 실행 수 | 기대 결과 |
| --- | --- | --- |
| 목록·단건·상대방 조회 × 두 참여자 | 6 | 유효 JWT로 CSRF 없이 200, 요청자에 맞는 상대방·미읽음·메시지·프로필 URL |
| 단건 미존재·비참여 | 2 | 같은 미존재 예외와 404 |
| 상대방 미존재·대화방 없음 | 2 | 같은 미존재 예외와 404 |
| 자기 자신을 상대방으로 지정 | 1 | 자기 대화 예외와 400 |

모든 경우에 대화방 수와 메시지 읽음 상태가 바뀌지 않는지 확인합니다.
정상 조회에서는 FileStorage Mock을 호출할 때 실제 DB 트랜잭션이 활성화되지 않았는지도 검사합니다.
이 검증은 상위 트랜잭션이 없는 HTTP 조회 경로에 대한 것으로, 다른 서비스의 트랜잭션 안에서 호출하는 경우까지 일반화하지 않습니다.

JWT 누락에 대한 조회 3종의 401 검증은 [ConversationSecurityIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationSecurityIntegrationTest.java)에 포함되어 있습니다.
같은 검증을 조회 전용 클래스에 중복하지 않고 [공통 보안 검증](04-testing.md#jwtcsrf-검증을-읽는-방법)과 함께 확인합니다.

## 실행 명령

조회 서비스·컨트롤러 테스트:

```bash
./gradlew test --tests '*ConversationQueryServiceTest' --tests '*ConversationQueryControllerTest'
```

실제 PostgreSQL 쿼리·JWT 요청 테스트(Docker 필요):

```bash
./gradlew test --tests '*ConversationQueryRepositoryTest' --tests '*ConversationQuerySecurityIntegrationTest'
```

조회 전용 4개 클래스 전체:

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.domain.conversation.*.ConversationQuery*Test'
```

2026-11-13 전체 회귀 실행에서 서비스 9건, 컨트롤러 21건, Repository 25건, 보안 통합 11건이 통과했습니다.
위 **66건은 당시 대화방 139건에 포함**됩니다. DM을 추가한 현재 전체 범위나 이번 문서 갱신으로 새로 실행한 결과가 아닙니다.

## Swagger 확인 항목

실행 환경과 접근 조건은 [API와 보안](01-api-security.md#swagger-문서-구성)을 참고합니다.
다음 항목을 OpenAPI JSON과 실제 Swagger UI에서 함께 확인합니다.

| 확인 대상 | 기대 내용 |
| --- | --- |
| 공통 | Bearer 인증 표시, SecurityUser·requesterId가 일반 입력으로 노출되지 않음 |
| 목록 입력 | keywordLike·cursor·idAfter 선택, limit·sortBy·sortDirection 필수. 총 6개 파라미터 |
| 단건·상대방 입력 | conversationId는 필수 경로 UUID, userId는 필수 쿼리 UUID. GET 요청 본문 없음 |
| 조회 응답 코드 | 목록 200/400/401/500, 단건·상대방은 404 추가. GET에 CSRF용 403 없음 |
| 생성 응답 코드 | 200/400/401/403/500과 생성 요청 본문 |
| 200 JSON 스키마 | 목록의 data 항목은 ConversationDto, 단건·상대방은 ConversationDto |
| 메시지 null | lastestMessage가 객체 또는 null이며 객체의 MessageDto 필드 유지 |
| 오류 JSON 스키마 | exceptionName·message·details의 공통 오류 응답 |

목록의 반환형에서 추론된 스키마가 `*/*` 아래에 생기면 `ConversationOpenApiConfig`가
스키마가 비어 있는 `application/json` 응답으로 옮깁니다. 메시지 null 허용도 같은 설정에서 보완합니다.
이 설정은 문서 표현만 조정하며 공용 DTO나 실제 응답을 변경하지 않습니다.
현재 설정은 DM 목록의 JSON 스키마도 연결합니다. 두 인터페이스를 하나의 `대화방` 그룹으로 표시하는 확인 항목은 [DM Swagger 검증](09-direct-message-testing.md#swagger-확인-항목)에 있습니다.

2026-11-13 조회 3종의 문서·UI 확인을 완료했습니다. 화면 표시 검증과 MockMvc의 실제 요청 검증을 구분합니다.
확인용 서버는 종료했으며, 기존 공용 Docker 서비스는 유지했습니다.

---

[← 테스트 읽기와 실행](04-testing.md) · [목차](README.md) · [다음: DM 메시지 조회 →](07-direct-message-query.md)
