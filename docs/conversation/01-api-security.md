# API와 보안

모든 API는 인증된 사용자를 요청자로 사용합니다. 성공 응답은 HTTP 200이며, 읽음 처리는 빈 본문, 나머지는 JSON을 반환합니다.

## 제공하는 API

| 메서드·경로 | 입력 | 성공 응답 |
| --- | --- | --- |
| `POST /api/conversations` | 본문의 `withUserId` | 신규 또는 기존 `ConversationDto` |
| `GET /api/conversations` | 검색·정렬·커서 파라미터 | `CursorResponse<ConversationDto>` |
| `GET /api/conversations/{conversationId}` | 경로의 대화방 UUID | 본인이 참여한 `ConversationDto` |
| `GET /api/conversations/with` | 필수 쿼리 파라미터 `userId` | 상대방과의 기존 `ConversationDto` |
| `GET /api/conversations/{conversationId}/direct-messages` | 경로 UUID, 정렬·커서 파라미터 | `CursorResponse<MessageDto>` |
| `POST /api/conversations/{conversationId}/direct-messages/{directMessageId}/read` | 경로 UUID 2개, 본문 없음 | 누적 읽음 처리 후 빈 본문 |

조회는 대화방을 생성하거나 메시지를 읽음 처리하지 않습니다.
목록 입력은 [대화방 검색·커서](05-search-pagination.md)와 [DM 메시지 조회](07-direct-message-query.md),
읽음 요청과 갱신 범위는 [DM 누적 읽음 처리](08-direct-message-read.md)를 참고합니다.

## 요청자와 상대방

| 값 | 출처 | 사용처 |
| --- | --- | --- |
| `requesterId` | 인증된 `SecurityUser` | 참여 검증, 상대방 선택, 미읽음 판단과 읽음 갱신 대상 제한 |
| `withUserId` | 생성 요청 본문 | 대화 상대 사용자 조회 |
| `userId` | 상대방 기준 조회의 쿼리 파라미터 | 요청자와 상대방으로 기존 대화방 조회 |

생성 요청 본문 예시:

```json
{
  "withUserId": "00000000-0000-0000-0000-000000000002"
}
```

컨트롤러는 요청을 검증한 뒤 인증된 사용자 ID를 넣어 생성·읽음 Command 또는 조회 조건을 만듭니다.
요청자 ID를 본문이나 쿼리 파라미터로 받지 않습니다.
필수 값은 Bean Validation, UUID 형식은 요청 변환 단계, 자기 대화 여부는 서비스에서 검사합니다.
단건 조회는 대화방 ID와 참여자 조건을 함께 적용하고, 상대방 기준 조회는 요청자·상대방의 정렬된 쌍을 사용합니다.
DM 조회·읽음 서비스는 먼저 대화방 참여 여부를 확인합니다.

## JWT와 CSRF

모든 요청에 Bearer JWT가 필요하며, 생성·읽음 POST에는 다음 CSRF 쿠키·헤더도 필요합니다.

| 위치 | 값 |
| --- | --- |
| `Authorization` 헤더 | `Bearer <Access Token>` |
| 쿠키 | `XSRF-TOKEN=<CSRF 토큰>` |
| `X-XSRF-TOKEN` 헤더 | 같은 CSRF 토큰 값 |

CSRF 토큰은 `GET /api/auth/csrf-token`으로 받을 수 있으며, 응답은 204와 `XSRF-TOKEN` 쿠키입니다.
쿠키에서 읽은 토큰 값을 헤더에 넣어 POST 요청을 보냅니다.
JWT가 유효해도 CSRF 쿠키·헤더가 없거나 일치하지 않으면 요청이 거절됩니다.
조회 GET은 CSRF 토큰 없이 호출할 수 있습니다. 실제 로그인 세션 유효성 확인은 공통 JWT 필터가 담당합니다.

## 성공 응답

생성·단건·상대방 기준 조회는 같은 응답 구조를 사용합니다. 다음은 메시지가 없는 대화방의 예시입니다.

```json
{
  "id": "00000000-0000-0000-0000-000000000010",
  "with": {
    "userId": "00000000-0000-0000-0000-000000000002",
    "name": "대화 상대",
    "profileImageUrl": null
  },
  "lastestMessage": null,
  "hasUnread": false
}
```

| 필드 | 의미 |
| --- | --- |
| `id` | 대화방 ID |
| `with` | 요청자를 기준으로 한 상대방의 사용자 요약 |
| `lastestMessage` | 최신 메시지. 메시지가 없으면 null |
| `hasUnread` | 요청자가 받은 메시지 중 읽지 않은 것이 있는지 여부 |

`lastestMessage` 철자는 공식 API와 프론트엔드의 필드 계약을 유지합니다.
메시지가 있으면 `MessageDto`가 `id`, `conversationId`, `createdAt`, `sender`, `receiver`, `content`를 반환합니다.
대화방 목록의 `data`에는 `ConversationDto`를 사용합니다. 검색된 메시지와 관계없이 실제 최신 메시지를 담으며,
`hasUnread`는 최신 메시지만이 아니라 요청자가 받은 전체 메시지에서 계산합니다.
실제 정적 리소스는 null인 최신 메시지를 `메시지 없음`으로 표시하며, 해당 메시지에 대한 읽음 요청을 보내지 않습니다.

DM 목록의 `data`에는 `MessageDto`를 사용하며 읽음 상태 필드는 포함하지 않습니다.
읽음 성공 응답은 JSON이나 갱신 건수 없이 HTTP 200으로 끝납니다. 갱신 후 미읽음 여부는 다음 대화방 조회에서 계산합니다.

## 오류 응답

오류 본문은 `exceptionName`, `message`, `details`의 공통 구조를 사용합니다.

| API | 주요 오류 |
| --- | --- |
| 생성 | 400: 상대방 ID 누락·형식 오류, 자기 대화, 상대방 미존재 / 403: CSRF 누락·불일치 |
| 대화방 목록 | 400: 필수 정렬값·limit 누락, 값·커서 형식 오류, 커서 두 값 중 하나만 전달 |
| 대화방 단건 | 400: 대화방 UUID 형식 오류 / 404: 대화방 미존재 또는 비참여 |
| 상대방 기준 | 400: userId 누락·형식 오류, 자기 자신 지정 / 404: 상대 사용자 또는 대화방 미존재 |
| DM 목록 | 400: UUID·필수 값·정렬·커서 오류 / 404: 대화방 미존재 또는 비참여 |
| DM 읽음 | 400: UUID 형식 오류 / 403: CSRF 누락·불일치 / 404: 대화방 미존재·비참여, 기준 메시지 미존재·다른 대화방 소속 |
| 공통 | 401: 인증·세션·사용자 상태 검증 실패 / 500: 조회·저장·응답 변환 중 서버 오류 |

대화방 목록에 결과가 없으면 빈 `data` 배열을 반환합니다. DM 목록은 대화방이 존재하고 참여한 경우에만 빈 배열로 성공할 수 있습니다.
단건 조회는 미존재와 비참여를 같은 `ConversationNotFoundException`으로 처리합니다.
상대방 기준 조회는 사용자 존재 여부를 별도로 확인하지 않고, 사용자 쌍 조회 결과가 없으면 같은 예외를 사용합니다.
읽음 기준 메시지가 없거나 다른 대화방에 있으면 `DirectMessageNotFoundException`을 사용합니다.

사용자 쌍 UNIQUE 충돌은 기존 대화방 재조회에 성공하면 200으로 처리합니다.
저장 커밋 후 프로필 이미지 URL 생성에 실패하면 500을 반환하되, 이미 생성된 대화방은 유지합니다.
저장과 응답 변환의 경계는 [트랜잭션 가이드](02-creation-transactions.md)를 참고합니다.

## Swagger 문서 구성

로컬 기본 포트의 Swagger UI는 `/swagger-ui.html`, OpenAPI JSON은 `/v3/api-docs`에서 확인합니다.
문서 GET 조회는 `dev`가 활성화되고 `prod`가 비활성화된 경우 인증 없이 허용됩니다.

`ConversationApi`는 대화방 4개, `DirectMessageApi`는 DM 2개 API의 계약과 인증 파라미터 숨김을 정의합니다.
인터페이스는 분리하되 같은 `대화방` 태그를 사용하여 UI의 한 그룹에 API 6개를 표시합니다.
`ConversationOpenApiConfig`는 두 목록의 제네릭 스키마를 JSON 응답에 연결하고,
`lastestMessage` 속성에 객체 또는 null을 허용하는 OpenAPI 3.1 `anyOf`를 적용합니다.
공용 `CursorResponse`·`MessageDto` 정의나 실제 JSON 응답을 바꾸는 설정은 아닙니다.

Swagger의 Bearer 인증 입력과 CSRF 전달은 별개이므로 실제 POST에는 위 쿠키·헤더도 필요합니다.
화면·스키마 확인 항목은 [대화방 조회 검증](06-query-testing.md#swagger-확인-항목)과 [DM·통합 표시 검증](09-direct-message-testing.md#swagger-확인-항목),
실제 요청 보안 검증은 [공통·생성 테스트](04-testing.md)와 각 조회·DM 테스트 문서에서 설명합니다.

## 관련 코드

[컨트롤러](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationController.java) ·
[Swagger 계약](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/swagger/ConversationApi.java) ·
[DM 컨트롤러](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/DirectMessageController.java) ·
[DM Swagger 계약](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/swagger/DirectMessageApi.java) ·
[OpenAPI 스키마 보정](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/config/ConversationOpenApiConfig.java) ·
[공통 보안 설정](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/SecurityConfig.java)

---

[← 목차](README.md) · [다음: 생성과 트랜잭션 →](02-creation-transactions.md)
