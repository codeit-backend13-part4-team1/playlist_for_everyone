# 3. 확장 지점과 후속 작업

이번 범위는 HTTP 요청입니다.
이미 열려 있는 SSE·웹소켓 연결은 세션이 종료되어도 유지됩니다.
아래 지점을 이용해 후속 작업에서 연결할 수 있습니다.

## 제공하는 지점

| 지점 | 용도 |
| --- | --- |
| `UserSessionRevokedEvent(userId, revokedSessionId, reason)` | 세션이 종료되었음을 알립니다. 중복 로그인·권한 변경·계정 잠금 모두 이 이벤트로 모입니다. |
| `UserSessionRepository.isCurrent(userId, sid)` | 연결 등록 직후 재확인이나 하트비트에서 세션 유효성을 확인할 수 있습니다. |
| `JwtTokenProvider.parseAccess(token)` | 토큰에서 사용자 ID와 `sid`를 함께 꺼냅니다. |

## 이벤트를 받을 때 주의할 점

`UserSessionRevokedEvent`는 일반 이벤트입니다. `@EventListener`로 받습니다.

권한 변경·계정 잠금에서는 이 이벤트가 이미 커밋이 끝난 `AFTER_COMMIT` 단계에서 발행됩니다.
이 시점에는 진행 중인 트랜잭션이 없으므로 `@TransactionalEventListener`는 기본 설정으로 실행되지 않습니다.

현재는 같은 서버 안에서만 발행합니다.
서버가 여러 대이면 연결이 다른 서버에 있을 수 있으므로, Redis 채널 등으로 전파하는 작업이 필요합니다.
