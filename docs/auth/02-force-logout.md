# 2. 강제 로그아웃

강제 로그아웃은 해당 사용자의 로그인 세션을 Redis에서 지우는 것입니다.
세션이 사라지면 그 로그인에서 발급한 Access·Refresh Token이 모두 거부됩니다.

## 사유별 처리

| 사유 | 발생 위치 | 세션 처리 | 이벤트 사유 |
| --- | --- | --- | --- |
| 중복 로그인 | 로그인 성공 처리 | `start()`가 기존 세션을 교체 | `DUPLICATE_LOGIN` |
| 권한 변경 | `UserAdministrationService.updateRole` | 커밋 후 `revoke()` | `ROLE_CHANGED` |
| 계정 잠금 | `UserAdministrationService.updateLocked(true)` | 커밋 후 `revoke()` | `LOCKED` |

같은 권한으로 변경 요청하거나 잠금을 해제하면 세션을 종료하지 않습니다.

## 권한 변경·계정 잠금 흐름

```text
관리자 PATCH /api/users/{id}/role 또는 /locked
  → UserAdministrationService (트랜잭션)
      → DB 변경
      → UserRoleChangedEvent 또는 UserLockedEvent 발행
  → 커밋
  → UserSessionRevokeListener (AFTER_COMMIT)
      → userSessionRepository.revoke(userId)
      → 종료한 세션이 있으면 UserSessionRevokedEvent 발행
```

사용자 도메인은 "권한이 바뀌었다", "계정이 잠겼다"는 사실만 발행합니다.
세션 종료는 인증 도메인이 이 이벤트를 받아 처리합니다.
`UserRoleChangedEvent`는 알림 도메인도 함께 구독합니다(#45).

## 왜 커밋 후에 처리하나요?

Redis는 DB 트랜잭션에 포함되지 않습니다.
트랜잭션 안에서 세션을 지우면, DB 변경이 롤백되어도 세션 삭제는 되돌릴 수 없습니다.
그러면 권한은 그대로인데 사용자만 로그아웃되는 상태가 됩니다.

`@TransactionalEventListener(phase = AFTER_COMMIT)`은 커밋에 성공한 경우에만 실행됩니다.
롤백되면 리스너가 실행되지 않으므로 사용자의 로그인이 유지됩니다.

## 세션 종료에 실패하면

DB 변경이 이미 커밋된 뒤라 관리자 요청을 실패로 응답하지 않습니다.
리스너는 `DataAccessException`을 잡아 `ERROR` 로그를 남깁니다.

```text
로그인 세션 종료에 실패했습니다. userId=..., reason=ROLE_CHANGED
```

세션이 남아도 권한 판단은 바뀐 값으로 합니다.
JWT 필터가 요청마다 DB에서 사용자를 조회하여 현재 권한으로 인증 정보를 만들고, 잠긴 계정은 인증하지 않기 때문입니다.
남는 문제는 "다시 로그인하게 만들기"가 적용되지 않는 것이므로, 로그를 확인해 조치합니다.

## 화면에는 언제 반영되나요?

현재는 **다음 요청에서** 거부합니다.
서버가 이전 기기에 먼저 알리지 않으므로, 이전 기기 화면은 사용자가 API를 호출하는 순간 로그인 화면으로 이동합니다.

```text
GET  /api/...          → 401
POST /api/auth/refresh → 401
→ 프론트가 로그인 화면으로 이동
```

같은 브라우저의 탭 두 개는 Refresh 쿠키를 공유하므로 서로 다른 기기로 보지 않습니다.
수동 확인은 일반 창과 시크릿 창처럼 쿠키가 분리된 환경에서 합니다.

즉시 반영은 SSE·웹소켓으로 서버가 먼저 알려야 하며, [확장 지점과 후속 작업](03-extension-points.md)에서 다룹니다.