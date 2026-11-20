# 12. WebSocket 보안과 공통 오류 처리

연결 설정과 오류 응답 도구는 콘텐츠·DM이 공유합니다. #61의 대화방 참여 인가는 DM 경로에 적용하며, 콘텐츠 전용 정책은 담당자 협의 후 별도로 적용합니다.

## 인증과 인가의 순서

`WebSocketConfig`는 인바운드 인터셉터를 다음 순서로 연결합니다.

1. `WebSocketInterceptor`: CONNECT의 JWT·현재 로그인 세션·사용자 상태를 확인하고 Principal을 설정합니다.
2. `SecurityContextChannelInterceptor`: 메시지의 인증 정보를 SecurityContext에 반영합니다.
3. `AuthorizationChannelInterceptor`: 공통 목적지 규칙을 적용하고 DM 참여 검증을 위임합니다.

`WebSocketAuthorizationConfig`가 정책을 구성하고, `DirectMessageAuthorizationManager`가 인증된 사용자와 대화방 참여 여부를 확인합니다. CONNECT의 채널 규칙이 permitAll이어도 앞선 JWT 검증을 생략하지 않습니다.

DM 인가 매니저는 인증 객체의 활성·잠금 상태와 경로 UUID를 확인합니다. 미존재·비참여는 거절하고, DB 장애를 권한 부족으로 바꾸지는 않습니다.

SEND·SUBSCRIBE 모두 UUID를 파싱한 뒤 소문자 정규 문자열과 정확히 일치하는지 비교합니다. 축약 표현과 대문자·혼합 표기는 대화방 참여 조회 전에 거절합니다. 실제 전달 목적지가 소문자 UUID로 구성되므로, 인가는 통과했지만 구독 경로의 대소문자가 달라 메시지를 받지 못하는 상황을 방지합니다.

## 목적지 규칙

아래는 **채널 인가 단계**의 규칙입니다. 먼저 매칭한 규칙을 사용하며, 컨트롤러·서비스의 업무 검증은 별도로 유지합니다.

| 메시지 | 현재 규칙 |
| --- | --- |
| CONNECT·HEARTBEAT·UNSUBSCRIBE·DISCONNECT | 채널 인가 통과. CONNECT 인증은 앞선 인터셉터에서 수행 |
| SEND `/pub/conversations/{conversationId}/direct-messages` | DM 참여 검증 |
| SUBSCRIBE `/sub/conversations/{conversationId}/direct-messages` | DM 참여 검증 |
| 위 두 경로 외 `/pub/conversations/**`, `/sub/conversations/**` | 거절. DM 수신 경로 직접 SEND·발행 경로 SUBSCRIBE 포함 |
| SUBSCRIBE `/sub/contents/**` | 기존 흐름 유지, DM 참여 검증 제외 |
| 나머지 SUBSCRIBE | 거절. `/sub/**`처럼 광범위한 구독 포함 |
| 나머지 메시지 | 기존 채널 통과 규칙 유지 |

목적지 방향을 검사하는 명시적인 DM 규칙은 대화방 접두사에 한정됩니다. 그 외 SUBSCRIBE의 기본 거절은 위 표와 같으며, 콘텐츠 전용 조건을 이번 DM 작업에서 추가한 것은 아닙니다. 콘텐츠의 허용 경로·패턴 구독·방향 우회·시청 참여 조건은 공통 설정의 협의 주석을 참고합니다.

## 동일 출처와 SockJS

프론트는 페이지와 같은 서버의 상대 경로 `/ws`로 연결합니다. 서버가 동일 출처로 판단하는 연결과 `EVERYONE_WEBSOCKET_ALLOWED_ORIGINS`에 명시한 Origin을 허용하며, 그 외 Origin은 STOMP CONNECT 이전의 HTTP 단계에서 거절됩니다. 기본 WebSocket과 SockJS에 같은 허용 목록을 적용합니다.

CloudFront 같은 프록시 뒤에서는 브라우저의 Origin과 서버가 인식하는 호스트·프로토콜이 달라질 수 있습니다. 운영 프론트의 Origin을 환경변수로 명시합니다. dev는 localhost 기본값을 제공하고 prod는 필수값 누락 시 기동에 실패합니다. 값 형식과 설정 예시는 [WebSocket 환경 설정](10-websocket-settings.md#공통-websocket-허용-origin)을 참고합니다.

SockJS의 XHR 요청을 위해 `SecurityConfig`는 `/ws/**`를 CSRF 검사에서 제외합니다. 다른 REST 쓰기 요청에는 기존 CSRF 보호가 적용됩니다. Origin 검사는 JWT 인증을 대신하지 않으며, 헤더를 제어할 수 있는 비브라우저 클라이언트에 대한 인증 수단도 아닙니다.

## 오류 처리의 두 진입점

| 발생 위치 | 진입점 | 공통 처리 연결 |
| --- | --- | --- |
| 채널 인가 등 프로토콜 처리 중 오류 | `WebSocketStompErrorHandler` | 대상 SEND·SUBSCRIBE와 세션 ID를 확인하여 전송기로 위임 |
| DM 메시지 매핑 중 입력 변환·검증·서비스 오류 | `DirectMessageWebSocketController.handleException` | `@MessageExceptionHandler`에서 요청 세션 ID로 전송기 호출 |
| 현재 목적지 목록 밖 또는 세션 ID 없는 프로토콜 오류 | Spring 기본 처리 | DM 공통 전송 경로를 강제로 적용하지 않음 |
| Redis 수신 등 별도 리스너 오류 | 각 수신 컴포넌트 | 송신 컨트롤러의 예외 처리기로 자동 전달되지 않음 |

두 진입점은 `WebSocketErrorSender` → `WebSocketErrorMapper`를 사용하여 `exceptionName`, `message`, `details`의 JSON 오류 본문을 구성합니다. HTTP 상태 응답이나 대화방 MESSAGE가 아니라 **요청 세션을 지정한 STOMP ERROR 프레임**입니다.

Mapper는 `MessageDeliveryException` 래퍼를 벗긴 뒤 업무 예외·인증·권한·변환·입력 검증 예외를 구분합니다. 업무 예외의 코드·상세값은 보존하고, 예상하지 못한 오류의 내부 메시지는 일반 서버 오류 메시지로 대체합니다. 같은 필드의 여러 검증 오류는 리스트로 유지합니다.

ERROR를 정상적으로 채널에 넘긴 이후 전송·연결 종료는 Spring STOMP 처리 흐름이 담당합니다. 직렬화·채널 전송 실패 또는 채널의 false 반환 시에는 Sender가 해당 연결 종료를 직접 시도합니다. 다른 사용자·다른 로그인 연결을 함께 종료하지 않습니다. 전송·종료 실패는 기록하며 자동 재시도하지 않습니다.

## 콘텐츠 담당자가 확장할 위치

- **인가:** `WebSocketAuthorizationConfig`의 콘텐츠 정책 협의 주석을 기준으로 적용 범위를 결정합니다. DM 인가 매니저에 콘텐츠 업무 검증을 넣지 않습니다.
- **프로토콜 오류:** `WebSocketStompErrorHandler`의 `ERROR_HANDLING_DESTINATION_PREFIXES`에 필요한 콘텐츠 접두사를 추가합니다. 이 목록은 접근 권한을 허용하는 화이트리스트가 아닙니다.
- **컨트롤러 내부 오류:** `DirectMessageWebSocketController.handleException`처럼 `@MessageExceptionHandler`에서 `WebSocketErrorSender`로 연결합니다. 접두사 추가만으로 컨트롤러 내부 처리까지 연결되지 않습니다.

확장 시 오류 형식과 ERROR 이후 연결 종료, 콘텐츠 시청·채팅 및 DM의 기존 동작을 함께 확인합니다.

## 로그인 세션 무효화와 후속 범위

현재는 무효화 이벤트가 공통 WebSocket 흐름으로 전달되면 해당 사용자·로그인 세션에 속한 연결을 종료합니다. 다중 서버에서는 [공통 이벤트 설정](10-websocket-settings.md#공통-websocket-이벤트와의-관계)을 활성화합니다.

DM 인가는 CONNECT 때 만들어진 인증 객체를 사용하며 매 SEND·SUBSCRIBE마다 JWT·Redis 세션의 자연 만료를 다시 조회하지 않습니다. 연결 유지 중 자연 만료 감지·종료는 [#208](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/208)의 후속 범위입니다. 현재 이벤트 기반 무효화 검증과 구분합니다.

---

[← 송수신 흐름](11-websocket-flow.md) · [목차](README.md) · [다음: 테스트 읽기 →](13-websocket-testing.md)
