# 1. API와 보안

`POST /api/conversations`는 로그인한 사용자와 지정한 상대방 사이의 대화방을 생성하거나 기존 대화방을 반환합니다.

## 요청자와 상대방

| 값 | 출처 | 사용처 |
| --- | --- | --- |
| `requesterId` | 인증된 `SecurityUser` | 상대방 선택과 요청자별 미읽음 판단 |
| `withUserId` | 요청 본문 | 대화 상대 사용자 조회 |

```json
{
  "withUserId": "00000000-0000-0000-0000-000000000002"
}
```

컨트롤러는 `ConversationCreateRequest`를 검증한 뒤 인증된 사용자 ID를 넣어 `ConversationCreateCommand`로 변환합니다.
요청 본문으로 요청자 ID를 지정하는 계약은 제공하지 않습니다.
상대방 ID의 필수 값은 Bean Validation, UUID 형식은 요청 변환 단계, 자기 대화 여부는 서비스에서 검사합니다.

## JWT와 CSRF

실제 생성 요청에는 다음 값이 필요합니다.

| 위치 | 값 |
| --- | --- |
| `Authorization` 헤더 | `Bearer <Access Token>` |
| 쿠키 | `XSRF-TOKEN=<CSRF 토큰>` |
| `X-XSRF-TOKEN` 헤더 | 같은 CSRF 토큰 값 |

CSRF 토큰은 `GET /api/auth/csrf-token`으로 받을 수 있으며, 응답은 204와 `XSRF-TOKEN` 쿠키입니다.
쿠키에서 읽은 토큰 값을 헤더에 넣어 POST 요청을 보냅니다.
JWT가 유효해도 CSRF 쿠키·헤더가 없거나 일치하지 않으면 요청이 거절됩니다.
실제 로그인 세션 유효성 확인은 공통 JWT 필터가 담당합니다.

## 성공 응답

신규 생성과 기존 대화방 반환 모두 **HTTP 200**입니다. 다음은 새 대화방의 응답 예시입니다.

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
| `id` | 신규 또는 기존 대화방 ID |
| `with` | 요청자를 기준으로 한 상대방의 사용자 요약 |
| `lastestMessage` | 최신 메시지. 메시지가 없으면 null |
| `hasUnread` | 요청자가 받은 메시지 중 읽지 않은 것이 있는지 여부 |

`lastestMessage` 철자는 공식 API와 프론트엔드의 필드 계약을 유지합니다.
메시지가 있으면 `MessageDto`가 `id`, `conversationId`, `createdAt`, `sender`, `receiver`, `content`를 반환합니다.
기존 대화방의 메시지와 미읽음 상태를 신규 초기값으로 덮어쓰지 않습니다.
실제 정적 리소스는 null인 최신 메시지를 `메시지 없음`으로 표시하며, 해당 메시지에 대한 읽음 요청을 보내지 않습니다.

## 오류 응답

오류 본문은 `exceptionName`, `message`, `details`의 공통 구조를 사용합니다.

| HTTP 상태 | 주요 조건 |
| --- | --- |
| 400 | 상대방 ID 누락·null·UUID 형식 오류, 자기 대화, 상대방 미존재 |
| 401 | JWT 누락·유효하지 않은 인증, 무효한 세션 또는 인증 사용자 상태 |
| 403 | CSRF 토큰 누락·불일치 |
| 500 | 복구할 수 없는 생성 실패, 응답 변환 중 예상하지 못한 오류 |

사용자 쌍 UNIQUE 충돌은 기존 대화방 재조회에 성공하면 200으로 처리합니다.
저장 커밋 후 프로필 이미지 URL 생성에 실패하면 500을 반환하되, 이미 생성된 대화방은 유지합니다.
저장과 응답 변환의 경계는 [트랜잭션 가이드](02-creation-transactions.md)를 참고합니다.

## Swagger 확인 방법

로컬 기본 포트의 Swagger UI는 `/swagger-ui.html`, OpenAPI JSON은 `/v3/api-docs`에서 확인합니다.
문서 GET 조회는 `dev`가 활성화되고 `prod`가 비활성화된 경우 인증 없이 허용됩니다.

1. **대화방 → POST /api/conversations**에서 Bearer 인증 표시와 요청 본문을 확인합니다.
2. 인증 객체가 일반 파라미터로 노출되지 않는지 확인합니다.
3. 200/400/401/403/500 설명과 응답 Schema를 확인합니다.
4. 200 응답의 `lastestMessage`가 `(object | null)`이고 객체 내부의 메시지 필드가 유지되는지 확인합니다.

`ConversationApi`가 API 설명을 제공하고, `ConversationOpenApiConfig`가 해당 속성만 OpenAPI 3.1의 `anyOf`로 보완합니다.
공용 `MessageDto` 전체를 null 허용으로 바꾸거나 실제 JSON 응답을 변경하는 설정은 아닙니다.
Swagger의 Bearer 인증 입력과 CSRF 토큰 전달은 별개이므로, 실제 요청에는 위 쿠키·헤더도 필요합니다.
기존 UI 확인은 문서 표시 검증이며, 실제 생성 요청의 보안 검증은 [JWT·CSRF 통합 테스트](04-testing.md)에서 수행합니다.

## 관련 코드

[컨트롤러](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/ConversationController.java) ·
[Swagger 계약](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/controller/swagger/ConversationApi.java) ·
[null 허용 스키마](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/config/ConversationOpenApiConfig.java) ·
[공통 보안 설정](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/config/SecurityConfig.java)

---

[← 목차](README.md) · [다음: 생성과 트랜잭션 →](02-creation-transactions.md)
