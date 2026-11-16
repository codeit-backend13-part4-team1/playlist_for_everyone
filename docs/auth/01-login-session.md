# 인증 구현 가이드

모두의 플리의 로그인, JWT, 로그인 세션, 강제 로그아웃 구조를 설명합니다.

**기준 범위** [#42 중복 로그인 차단 및 강제 로그아웃](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/42)의 HTTP 요청 처리입니다.
SSE·웹소켓 연결 종료는 후속 작업이며, 연결할 수 있는 지점만 제공합니다.

## 토큰 설명
| 개념 | 설명                                                                       |
| --- | --- |
| Access Token | 요청마다 `Authorization: Bearer`로 보내는 JWT입니다. 유효기간 15분이며 프론트 메모리에만 보관합니다. |
| Refresh Token | Access Token 재발급용 JWT입니다. 유효기간 7일이며 HttpOnly 쿠키 `REFRESH_TOKEN`으로 전달합니다. |
| 로그인 세션 | 한 번의 로그인을 나타내는 ID(`sid`)입니다. 사용자당 하나만 유효하며 Redis에 저장합니다. |

두 토큰에는 같은 `sid`가 들어갑니다.
JWT 서명이 유효해도 `sid`가 Redis의 현재 세션과 다르면 인증하지 않습니다.
이것으로 중복 로그인 차단과 강제 로그아웃을 처리합니다.

## Redis 저장 구조

| 키 | 값 | TTL | 담당 |
| --- | --- | --- | --- |
| `auth:session:<사용자 ID>` | 현재 로그인 세션 ID | Refresh Token 유효기간 | `RedisUserSessionRepository` |
| `auth:refresh:<토큰 SHA-256>` | 사용자 ID | Refresh Token 유효기간 | `RedisRefreshTokenRepository` |

세션 키는 사용자당 하나입니다. 새 로그인이 값을 덮어쓰는 것 자체가 이전 로그인의 무효화입니다.
값에는 토큰 원문이 아니라 무작위 세션 ID를 저장하므로, 값이 노출되어도 그것만으로는 인증할 수 없습니다.

## 저장소 메서드

| 메서드 | 사용하는 곳 | 동작 |
| --- | --- | --- |
| `start(userId, ttl)` | 로그인 성공 | 새 세션으로 교체하고, 교체된 이전 세션 ID를 함께 반환합니다. |
| `isCurrent(userId, sid)` | JWT 필터, 재발급 | 현재 세션인지 확인합니다. GET 한 번입니다. |
| `extendIfCurrent(userId, sid, ttl)` | 재발급 | 현재 세션일 때만 TTL을 연장합니다. |
| `endIfCurrent(userId, sid)` | 로그아웃 | 현재 세션일 때만 삭제합니다. |
| `revoke(userId)` | 권한 변경·계정 잠금 | 조건 없이 삭제하고 삭제한 세션 ID를 반환합니다. |

`start`, `extendIfCurrent`, `endIfCurrent`는 "확인 후 변경"이므로 Lua 스크립트로 한 번에 실행합니다.
나누어 실행하면 그 사이에 새 로그인이 끼어들어, 이전 기기의 로그아웃이 새 세션을 지우는 문제가 생길 수 있습니다.

## 요청별 처리

### 로그인 (`JwtAuthenticationSuccessHandler`)

1. 응답에 필요한 사용자 정보를 조회합니다. 실패하면 기존 세션을 교체하지 않습니다.
2. `start()`로 세션을 시작합니다. 기존 세션은 이 시점에 무효가 됩니다.
3. 같은 `sid`로 Access·Refresh Token을 발급합니다.
4. Refresh Token을 Redis에 저장합니다.
5. 교체된 세션이 있으면 `UserSessionRevokedEvent(DUPLICATE_LOGIN)`을 발행합니다.
### 인증이 필요한 요청 (`JwtAuthenticationFilter`)

1. `parseAccess()`로 서명·만료·종류·`sid`를 검증합니다.
2. `isCurrent()`로 현재 세션인지 확인합니다. 아니면 DB를 조회하지 않고 401로 응답합니다.
3. DB에서 사용자를 조회하고 잠금 여부를 확인합니다.
4. 권한은 토큰이 아니라 DB의 현재 값으로 `SecurityUser`를 만듭니다.
   세션이 무효인 경우도 만료·변조 토큰과 같은 401 응답(`UnauthorizedException`)을 반환합니다.
   클라이언트는 원인을 구분하지 않고 재발급을 시도하면 됩니다.

### 재발급 (`AuthService.reissue`)

1. `parseRefresh()`로 검증합니다.
2. `isCurrent()`로 확인합니다. Redis에 Refresh Token이 남아 있어도 세션이 교체되었으면 거부합니다.
3. Redis 등록 여부, 토큰 소유자, 사용자 존재·잠금을 확인합니다.
4. 같은 `sid`로 새 토큰을 발급하고 Refresh Token을 원자적으로 교체합니다.
5. `extendIfCurrent()`로 세션 TTL을 연장합니다. 그 사이 세션이 종료되었으면 새 토큰을 반환하지 않습니다.
### 로그아웃 (`AuthService.logout`)

1. Refresh Token에서 `sid`를 읽을 수 있으면 `endIfCurrent()`로 세션을 종료합니다.
2. Refresh Token 등록 정보를 삭제합니다.
   세션을 종료하므로 이 로그인의 Access Token도 다음 요청부터 401입니다.
   다른 기기에서 이미 다시 로그인했다면 그 세션은 유지합니다.
   만료·변조된 토큰이라 `sid`를 읽을 수 없어도 로그아웃 자체는 실패시키지 않습니다.

## Redis 장애 시

| 위치 | 동작 |
| --- | --- |
| JWT 필터 | 세션을 확인할 수 없으므로 인증을 통과시키지 않습니다. |
| 로그인·재발급·로그아웃 | 예외를 호출자에게 전달합니다. 성공한 것처럼 응답하지 않습니다. |
| 강제 로그아웃 리스너 | 오류 로그를 남기고 관리자 요청은 성공으로 둡니다. [강제 로그아웃](02-force-logout.md#세션-종료에-실패하면) 참고 |

로그인 중 세션 교체 직후 Refresh Token 저장이 실패하면, 기존 세션은 이미 교체되어 다시 로그인해야 합니다.
두 작업이 같은 Redis에서 연속으로 일어나므로 그 사이 장애가 난 경우이며, 알려진 제한으로 둡니다.
