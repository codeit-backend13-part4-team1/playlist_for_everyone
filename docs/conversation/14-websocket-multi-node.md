# 14. 두 서버에서 DM 송수신 확인

팀원이 기존 Compose와 제공된 프론트로 DM 전달을 확인하는 수동 절차입니다. 이 문서의 작성이 수동 검증 완료를 뜻하지는 않습니다. 자동 테스트의 환경·결과와 구분하여 실제 수행한 결과를 기록합니다.

## 1. 서버 준비

[기존 다중 서버 실행 문서](../sse/07-redis-multi-node.md)에 따라 bootJar를 만들고 `compose.yaml`과 `compose.sse.yml`로 `app-a`·`app-b`를 실행합니다. 두 앱은 같은 DB·Redis·JWT 설정을 사용합니다.

`.env.dev`에서 다음 설정을 확인합니다. 자세한 기본값과 적용 방법은 [환경 설정](10-websocket-settings.md)을 참고합니다.

| 설정 | 확인할 값 |
| --- | --- |
| `EVERYONE_DM_WEBSOCKET_TRANSPORT` | `redis` |
| `EVERYONE_DM_WEBSOCKET_SUBSCRIPTION_ENABLED` | `true` |
| `EVERYONE_DM_WEBSOCKET_REDIS_CHANNEL` | 두 서버가 같은 DM 채널 사용 |
| `EVERYONE_WEBSOCKET_EVENT_MULTIPLE` | `true`, 공통 세션 무효화의 서버 간 전달 |

설정이나 코드를 바꾸었다면 기존 실행 문서의 재빌드·앱 재생성 절차를 적용합니다. 프로세스 실행 상태와 두 앱의 기동 완료를 확인한 뒤 진행합니다.

## 2. 사용자와 브라우저 준비

| 구분 | 접속 주소 | 로그인 |
| --- | --- | --- |
| 서버 A | `http://localhost:8081` | 사용자 A |
| 서버 B | `http://localhost:8082` | 사용자 B |

일반 창과 시크릿 창 또는 서로 다른 브라우저 프로필을 사용합니다. 포트만 다르게 열어도 같은 호스트의 쿠키는 공유될 수 있으므로 두 사용자의 쿠키 저장소를 분리합니다.

서로 다른 로컬 검증 계정으로 로그인합니다. 같은 계정으로 다시 로그인하면 이전 로그인 연결이 무효화될 수 있어 전달 검증을 방해합니다. 실제 계정·토큰을 문서나 공유 캡처에 남기지 않습니다.

프로필 화면의 대화 시작 기능으로 두 사용자 사이의 대화방을 만든 뒤 양쪽에서 같은 대화방을 엽니다. 양쪽의 대화방 ID가 같은지 확인합니다. 대화방을 미리 준비할 때의 REST 계약은 [API와 보안](01-api-security.md)을 참고합니다.

## 3. 연결과 구독 확인

각 창의 개발자 도구에서 Network의 WebSocket·SockJS 요청을 확인합니다.

- A 화면은 `localhost:8081`, B 화면은 `localhost:8082`의 `/ws`에 연결되어야 합니다. 각 페이지의 상대 경로 연결을 사용합니다.
- STOMP CONNECT 이후 CONNECTED와 같은 대화방의 `/sub/conversations/{conversationId}/direct-messages` SUBSCRIBE를 확인합니다.
- SockJS는 `/ws/{server}/{session}/websocket`처럼 하위 경로를 사용할 수 있고, XHR 폴백에서는 WebSocket 항목 대신 HTTP 요청으로 보일 수 있습니다.

포트를 바꾼 소켓 주소를 한 화면에 강제로 지정하지 않습니다. 이는 다른 출처가 되어 기존 연결 제한과 충돌할 수 있습니다.

## 4. 양방향 전달과 재조회

1. A에서 `DM-A-to-B-검증시각`처럼 구별 가능한 본문을 한 번 보냅니다.
2. B가 새로고침 없이 받는지 확인합니다. A·B의 수신 데이터에서 `id`, `conversationId`, `content`가 같은지 비교합니다.
3. B에서도 다른 본문으로 한 번 보내고 A 수신을 확인합니다.
4. 실제 메시지 ID 기준으로 중복 MESSAGE 수신이 있는지 관찰합니다. 하트비트는 메시지 건수에서 제외합니다.
5. 대화방을 다시 열어 REST 메시지 목록에 같은 ID가 남아 있는지 확인합니다. 대화가 많다면 테스트 메시지가 포함된 페이지를 확인합니다.

프론트는 DM 수신 후 읽음 API를 별도로 호출합니다. 따라서 여기서 미읽음 상태 유지가 성공 조건은 아닙니다. 화면 표시만으로 DB 행 수나 모든 상황의 중복 방지를 확정하지 않으며, DB 저장·정해진 시간 내 중복 검증은 [자동 통합 테스트](13-websocket-testing.md)로 구분합니다.

## 문제가 있을 때

| 증상 | 확인할 항목 |
| --- | --- |
| REST 로그인·생성·읽음 403 | CSRF 쿠키·헤더와 브라우저 쿠키 분리 |
| `/ws` HTTP 단계 403 | 페이지와 연결의 출처, 프록시 설정 |
| CONNECT 오류·즉시 종료 | JWT·현재 로그인 세션·사용자 잠금 상태 |
| DM SEND·SUBSCRIBE 거절 | 정확한 경로·UUID·대화방 참여 여부 |
| 같은 서버만 수신 | B의 자동 구독, Redis 접속·DM 채널 일치, 실제 B 포트 연결 여부 |
| `persisted=true` 오류 | 새로 SEND하기 전에 오류의 메시지 ID로 REST 목록 확인 |

서버 로그는 기존 Compose 실행 문서의 `logs` 명령으로 확인합니다. Redis 구독자 수는 브라우저의 수신 성공 건수가 아니며, 수신 확인을 대신하지 않습니다.

## 결과 기록과 종료

검증한 커밋·설정, A/B의 실제 연결 포트, 양방향 메시지 ID·수신 및 REST 재조회 결과를 기록합니다. 자동 테스트는 별도 Testcontainers와 독립 Spring 컨텍스트로 실행되므로 위 Compose 검증을 대신 수행한 것으로 기록하지 않습니다.

두 브라우저 연결을 닫고 [앱 종료 절차](../sse/07-redis-multi-node.md#검증과-종료)의 `stop app-a app-b`를 실행합니다. 공유 DB·Redis와 검증 계정·메시지는 유지하며 볼륨 삭제는 필요하지 않습니다.

---

[← 테스트 읽기](13-websocket-testing.md) · [목차](README.md) · [환경 설정](10-websocket-settings.md)
