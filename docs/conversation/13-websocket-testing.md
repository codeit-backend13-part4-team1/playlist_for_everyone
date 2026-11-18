# 13. DM WebSocket 테스트 읽기와 실행

현재 소스 구성은 대화방·DM 도메인 **35개 클래스**와 관련 공통 WebSocket **6개 클래스**, 합계 **41개 클래스**입니다. 기존 #58~#60의 18개에 #61의 도메인 17개·공통 6개가 추가되었습니다. 이는 클래스 수이며 파라미터화된 테스트 실행 건수나 최신 통과 건수와 다릅니다.

## 경계별로 확인하는 것

테스트 코드는 [대화방·DM](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/conversation), [공통 설정](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/config), [공통 오류](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/websocket/error)에 있습니다.

| 경계 | 대표 테스트 | 확인하는 범위·한계 |
| --- | --- | --- |
| 입력·서비스 | `DirectMessageSendRequestTest`, `DirectMessageSendServiceTest` | 입력·호출 순서·실패 전달. 실제 커밋·Redis 수신 제외 |
| 독립 저장 트랜잭션 | `DirectMessageSendServiceIntegrationTest` | PostgreSQL 커밋·저장 실패·외부 롤백·변환/발행 실패 후 데이터 유지. FileStorage·Publisher는 Mock |
| 메시지 매핑 | `DirectMessageWebSocketControllerTest`, `DirectMessageWebSocketMessageMappingTest`, `DirectMessageWebSocketErrorHandlingTest` | 인증 사용자 전달·메시지 매핑·컨트롤러 예외 연결. 실제 네트워크 수신과 구분 |
| 전송 설정·생명주기 | `DirectMessageTransport*Test`, `RedisDirectMessageSubscription*Test` | 설정 검증·조건부 빈·등록/해제. 일반 테스트는 자동 구독 비활성화 |
| 브로커·로컬 전달 | `RedisDirectMessagePublisherTest`, `RedisDirectMessageSubscriberTest`, `DirectMessageDeliveryServiceTest` | 직렬화·발행 실패·JSON 건너뛰기·로컬 목적지와 전송 실패 |
| 실제 Redis | `RedisDirectMessagePubSubIntegrationTest` | 실제 Pub/Sub·독립 컨텍스트 수신. SimpMessagingTemplate은 Mock이므로 브라우저 수신·DB 저장 제외 |
| 채널 인가·공통 오류 | `DirectMessageAuthorizationManagerTest`, 공통 WebSocket 6개 | DM 경로·참여 제한·기존 콘텐츠 통과 규칙·오류 변환/세션 전송·빈 연결 |
| 실제 연결·송수신 | `DirectMessageWebSocketIntegrationTest` | 실제 포트·JWT·Redis 세션·DB·STOMP, 출처 제한·XHR 폴백·분산 전달·REST 재조회 |
| 분산 세션 무효화 | `DirectMessageWebSocketSessionRevocationIntegrationTest` | 무효화된 로그인 연결만 A·B에서 종료, 새 로그인·다른 사용자 연결 유지 |

공통 6개는 `WebSocketAuthorizationConfigTest`, `WebSocketInboundSecurityTest`, `WebSocketStompErrorHandlerConfigTest`, `WebSocketErrorMapperTest`, `WebSocketErrorSenderTest`, `WebSocketStompErrorHandlerTest`입니다.

## 실제 연결 테스트의 의미

- PostgreSQL 16·Redis 7.2.6을 Testcontainers로 실행하며 개발 DB·Compose 서버를 사용하지 않습니다.
- 실제 서명된 JWT와 Redis 로그인 세션을 준비합니다. 로그인 API 호출 자체를 검증하는 것은 아닙니다.
- FileStorage는 Mock입니다. 송수신 테스트의 Publisher·DeliveryService Spy는 정상 구현을 사용하고 실패 주입·호출 관찰에 활용합니다.
- 추가 서버 B는 같은 JVM의 독립 Spring 컨텍스트와 다른 HTTP 포트입니다. 별도 OS 프로세스·리버스 프록시 검증과는 구분합니다.
- 중복 검증은 정해진 관찰 시간 내 수신과 DB 상태를 확인합니다. 장애 상황 전체의 exactly-once 보장은 아닙니다.
- 분산 무효화 테스트는 로그인 세션 교체·이벤트를 직접 준비합니다. 시간 경과에 따른 인증 자연 만료는 #208의 범위입니다.

브라우저는 수신 후 읽음 API를 호출하지만 STOMP 테스트 클라이언트는 이를 자동 호출하지 않습니다. 따라서 송수신 테스트의 미읽음 유지 조건을 브라우저 검증에 그대로 적용하지 않습니다.

## 실행 명령

프로젝트 루트에서 Docker를 실행한 상태로 수행합니다. 일반 테스트의 구독 기본값과 통합 테스트의 활성화는 [환경 설정](10-websocket-settings.md#테스트와-설정-검증)을 참고합니다.

실제 연결·분산 세션 무효화와 REST CSRF 경계:

```bash
./gradlew test \
  --tests '*DirectMessageWebSocketIntegrationTest' \
  --tests '*DirectMessageWebSocketSessionRevocationIntegrationTest' \
  --tests '*ConversationSecurityIntegrationTest.create_returnsForbiddenWhenCsrfMissing' \
  --no-daemon
```

서버 간 전달·무효화 두 사례만 확인:

```bash
./gradlew test \
  --tests '*DirectMessageWebSocketIntegrationTest.send_deliversAcrossActualServersWithoutDuplicatingMessages' \
  --tests '*DirectMessageWebSocketSessionRevocationIntegrationTest.sessionRevoked_closesMatchingConnectionsAcrossServers' \
  --no-daemon
```

대화방·DM과 관련 공통 WebSocket 전체 41개 클래스:

```bash
./gradlew test \
  --tests 'com.codeit.sb13.playlist_for_everyone.domain.conversation.*' \
  --tests '*WebSocketAuthorizationConfigTest' \
  --tests '*WebSocketInboundSecurityTest' \
  --tests '*WebSocketStompErrorHandlerConfigTest' \
  --tests 'com.codeit.sb13.playlist_for_everyone.global.websocket.error.*' \
  --no-daemon
```

## 결과 기록 기준

실행한 클래스·테스트 이름, 실패·오류·건너뜀은 `build/reports/tests/test/index.html`과 `build/test-results/test/`에서 확인합니다. 클래스 수만으로 테스트 실행 완료를 판단하지 않습니다.

[기존 공통 테스트 기록](04-testing.md#기존-검증-기록)과 [조회·읽음 기록](09-direct-message-testing.md)은 당시 범위를 유지합니다. 이번 주석·문서 정리에서는 테스트를 재실행하지 않으므로 새 전체 통과 수치를 기록하지 않습니다. 일부 테스트의 JaCoCo 결과를 프로젝트 전체 커버리지로 해석하지 않습니다.

---

[← 보안과 오류](12-websocket-security-errors.md) · [목차](README.md) · [다음: 다중 서버 수동 검증 →](14-websocket-multi-node.md)
