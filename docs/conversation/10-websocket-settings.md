# 10. DM WebSocket 환경 설정

DM 메시지는 DB 저장·커밋 후 Redis Pub/Sub으로 발행하고, 각 서버가 수신하여 자신의 WebSocket 구독자에게 전달합니다.
이 문서는 전송·구독 설정과 공통 연결 Origin 설정을 설명합니다. 환경변수 예시는 [.env.example](../../.env.example), 전송·구독 기본값은 [application.yaml](../../src/main/resources/application.yaml)을 기준으로 합니다. 허용 Origin은 아래와 같이 프로필별로 설정합니다.

## 공통 WebSocket 허용 Origin

`EVERYONE_WEBSOCKET_ALLOWED_ORIGINS`는 `everyone.websocket.allowed-origins`에 바인딩됩니다. **콘텐츠 시청·채팅과 DM이 공유하는 기본 WebSocket 및 SockJS 연결에 모두 적용됩니다.**

| 프로필 | Origin 설정 |
| --- | --- |
| `dev` | 환경변수 미지정 시 `http://localhost:${server.port:8080}` |
| `prod` | 환경변수 필수. 기본값 없음 |
| `test` | `application-test.yaml`의 `http://localhost:8080` 사용 |

공통 `application.yaml`에는 Origin 기본값을 두지 않습니다. `WebSocketOriginProperties` record가 목록과 주소 형식을 검증하므로 설정 누락·빈 값·잘못된 주소는 기동 실패로 확인할 수 있습니다. dev에서도 환경변수를 빈 값으로 지정하면 기본값으로 대체하지 않습니다.

배포 환경에는 프론트 페이지의 Origin을 지정합니다.

```dotenv
EVERYONE_WEBSOCKET_ALLOWED_ORIGINS=https://d3vqh3u26il5vi.cloudfront.net
```

여러 주소는 `https://frontend.example,http://localhost:8080`처럼 쉼표로 구분합니다. `http://` 또는 `https://`와 호스트·필요한 포트만 사용하며, 경로·마지막 `/`·쿼리·사용자 정보·와일드카드는 넣지 않습니다. `.env.example`의 예시는 주석 상태이므로 운영에서는 배포 환경변수에 실제 값을 주입하고 재배포해야 합니다.

브라우저가 보내는 Origin과 일치하도록 HTTP의 `80`, HTTPS의 `443` 포트는 검증 후 생략합니다. 예를 들어 `https://frontend.example:443`은 `https://frontend.example`로 정규화합니다. `8080`·`8443` 등 비기본 포트와 IPv6 주소의 대괄호는 유지합니다.

서버가 동일 출처로 판단하는 연결은 계속 허용됩니다. 허용 목록은 프록시 뒤에서 다른 출처로 인식되는 프론트의 연결을 추가로 허용하며, STOMP CONNECT의 JWT 인증과 목적지별 인가를 생략하지 않습니다. 배포 후에는 `/ws/info`의 Origin 포함 요청, WebSocket 업그레이드, STOMP CONNECT 순서로 확인합니다.

## DM 전용 설정

아래 설정 경로의 공통 접두사는 `everyone.dm.websocket`입니다.

| 환경변수 | 접두사 이후 경로 | 애플리케이션 기본값 |
| --- | --- | --- |
| `EVERYONE_DM_WEBSOCKET_TRANSPORT` | `transport` | `redis` |
| `EVERYONE_DM_WEBSOCKET_SUBSCRIPTION_ENABLED` | `subscription-enabled` | `true` |
| `EVERYONE_DM_WEBSOCKET_REDIS_CHANNEL` | `redis.channel` | `everyone:dm:events` |

- **전송 방식:** 현재 Redis 구현체만 지원합니다. `kafka`를 지정하는 것만으로 전환할 수 없습니다.
- **구독 여부:** `true`이면 서버 시작 시 DM 채널을 구독하고 종료 시 해제합니다. `false`이면 이 서버의 DM 자동 구독만 비활성화합니다. 메시지 저장·Redis 발행과 다른 기능의 Redis 사용은 유지됩니다.
- **채널:** 같은 환경의 서버는 같은 Redis의 동일 채널을 구독해야 서로 전달할 수 있습니다. 구독을 끈 서버의 클라이언트는 이 경로로 실시간 DM을 받지 못합니다.

채널은 Redis Pub/Sub 내부 주소입니다. 클라이언트의 STOMP 목적지인 `/pub/conversations/{conversationId}/direct-messages`와 `/sub/conversations/{conversationId}/direct-messages`는 변경하지 않습니다.

## 환경별 채널 지정

개발에서는 `.env.dev`, 운영에서는 배포 환경변수에 값을 지정합니다. 아래는 환경을 구분하는 예시이며, 프로필만 바꿔서는 자동 적용되지 않습니다.

| 환경 | `EVERYONE_DM_WEBSOCKET_REDIS_CHANNEL` 지정 예시 |
| --- | --- |
| 개발 | `everyone:dev:dm:events` |
| 운영 | `everyone:prod:dm:events` |

환경변수를 생략하면 두 환경 모두 `everyone:dm:events`를 사용합니다. 다른 환경과 Redis를 공유한다면 DM 채널을 구분하고, 같은 환경의 모든 서버에는 같은 값을 적용합니다.

Redis Pub/Sub은 메시지 이력을 보관하지 않습니다. 채널 변경 중 서로 다른 채널을 사용하는 서버 사이에는 실시간 전달이 되지 않으며, 누락된 메시지는 DB 기반 DM 조회 API로 확인합니다.

## 공통 WebSocket 이벤트와의 관계

| 환경변수 | 설정 경로 | 애플리케이션 기본값 |
| --- | --- | --- |
| `EVERYONE_WEBSOCKET_EVENT_MULTIPLE` | `everyone.websocket.event.multiple` | `true` |

이 설정은 세션 무효화 등 공통 WebSocket 이벤트를 여러 서버로 전달할지 결정합니다. `false`는 단일 서버 내에서 처리합니다. DM 메시지 전송 방식과는 별개이며, 여러 서버에서 세션 무효화를 반영하려면 `true`를 사용합니다.

DM 채널을 바꿔도 공통 인증·콘텐츠 이벤트 채널은 바뀌지 않습니다. 예를 들어 세션 무효화는 별도의 `websocket:event:auth:revoke` 채널을 사용합니다. **DM 채널 분리만으로 전체 WebSocket 이벤트의 환경 격리가 완료되는 것은 아닙니다.**

## 테스트와 설정 검증

[테스트 프로필](../../src/test/resources/application-test.yaml)은 DM 자동 구독과 공통 이벤트의 분산 처리를 모두 `false`로 설정합니다. 일반 테스트가 자동 구독을 준비하지 않아도 되도록 하는 기본 구성입니다.

DM Redis 통합 테스트에서는 `everyone.dm.websocket.subscription-enabled=true`와 테스트 전용 채널을 지정합니다. 서버 간 세션 무효화를 검증하는 테스트는 `everyone.websocket.event.multiple=true`도 별도로 지정합니다.

[DirectMessageTransportProperties](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/config/DirectMessageTransportProperties.java)는 미지원 전송 방식, Redis 설정 누락, null·빈 문자열·공백 채널을 거절합니다. 환경변수 생략 시에는 YAML 기본값이 적용되지만, 빈 값 지정은 기본값 사용과 다릅니다. 자동 구독을 꺼도 전송 설정 검증은 유지됩니다.

## 기존 Compose 구성 재사용

[compose.sse.yml](../../compose.sse.yml)의 `app-a`와 `app-b`는 같은 `.env.dev`를 컨테이너 환경변수로 전달받습니다. 파일 이름과 관계없이 기존 두 앱 구성을 DM 검증에도 재사용할 수 있습니다.

- DM 관련 값이 이미 있는 `.env.dev`라면 그 값을 사용합니다. `.env.example` 변경이 기존 `.env.dev`에 자동 반영되지는 않습니다.
- DM 전송·구독 값이 없으면 애플리케이션 기본값인 `redis`·`true`를 사용합니다. Compose가 강제로 켜는 SSE 구독과는 별도입니다.
- 같은 DB·Redis와 JWT 설정을 사용하며, 호스트에서 서버 A는 `8081`, 서버 B는 `8082`로 접근합니다.

빌드·실행·종료는 [기존 다중 서버 실행 절차](../sse/07-redis-multi-node.md), DM 전달 확인은 [다중 서버 수동 검증](14-websocket-multi-node.md)을 참고합니다. 자동 테스트의 환경·검증 범위는 [테스트 가이드](13-websocket-testing.md)에서 구분합니다.

---

[← 이전: DM 테스트와 Swagger 검증](09-direct-message-testing.md) · [목차](README.md) · [다음: 송수신 흐름 →](11-websocket-flow.md)
