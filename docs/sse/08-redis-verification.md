# 8. SSE 연결 확인과 실제 수신 검증

수동 절차는 두 프로세스의 인증·연결·하트비트를 확인합니다.
알림 저장부터 실제 이벤트 수신·재연결 복구까지는 아래 자동 통합 테스트로 검증합니다.
[다중 서버 실행](07-redis-multi-node.md)을 먼저 완료합니다.

## 1. CSRF 토큰 준비와 로그인

현재 보안 설정은 CSRF 보호를 사용합니다. GET /api/sse와 달리 회원가입·로그인 POST에는 토큰이 필요합니다.
프로젝트 루트에서 임시 쿠키 파일을 만들고 공개 CSRF 발급 API를 호출합니다.

```bash
SSE_COOKIE_JAR="$(mktemp)"
curl --silent --show-error --max-time 10 \
  -b "$SSE_COOKIE_JAR" -c "$SSE_COOKIE_JAR" \
  http://localhost:8081/api/auth/csrf-token
SSE_CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$SSE_COOKIE_JAR")"
```

발급 API는 204를 반환하고 XSRF-TOKEN 쿠키를 설정합니다.
헤더 X-XSRF-TOKEN에는 그 토큰 값을 넣고, 같은 쿠키 파일을 함께 보냅니다.

기존 로컬 검증 계정을 사용합니다. 계정이 없다면 먼저 고유한 예시 값으로 회원가입합니다.

```bash
curl -i --max-time 10 -X POST http://localhost:8081/api/users \
  -b "$SSE_COOKIE_JAR" -c "$SSE_COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $SSE_CSRF_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"email":"sse-check@example.com","name":"SSE 검증 사용자","password":"local-sse-check"}'
```

회원가입 성공은 201입니다. 로그인은 JSON이 아닌 폼 데이터의 username·password를 사용합니다.
쿠키가 만료되었거나 토큰이 비어 있으면 위 발급 절차부터 다시 수행합니다.

```bash
curl -i --max-time 10 -X POST http://localhost:8081/api/auth/sign-in \
  -b "$SSE_COOKIE_JAR" -c "$SSE_COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $SSE_CSRF_TOKEN" \
  --data-urlencode 'username=sse-check@example.com' \
  --data-urlencode 'password=local-sse-check'
```

성공 응답 200의 accessToken을 다음 예시의 <ACCESS_TOKEN>에 넣습니다.
로그인 이후 쓰기 요청을 추가로 한다면 CSRF 토큰을 다시 발급받아 사용합니다.
실제 토큰·계정 정보는 문서에 저장하지 않습니다.

## 2. 두 서버의 연결 확인

터미널 A와 B에서 같은 JWT로 각각 연결합니다.

```bash
curl -i -N --max-time 120 http://localhost:8081/api/sse \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

```bash
curl -i -N --max-time 120 http://localhost:8082/api/sse \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

두 응답의 200, text/event-stream, 최초 :connected와 이후 :heartbeat를 확인합니다.
GET 연결에는 CSRF 헤더가 필요하지 않습니다.
이 단계는 연결·하트비트 확인이며 알림 저장이나 복구까지 검증한 것은 아닙니다.

## 3. 실제 알림 수신과 복구 검증

현재는 Redis CLI로 임의 JSON을 PUBLISH하는 것만으로 알림을 보낼 수 없습니다.
수신 신호 이후 실제 데이터는 공유 이력에서 읽고, 알림 필터는 DB에 남은 본인 알림을 확인합니다.
임의 payload 발행이나 수동 DB·Redis 삽입 대신 기존 통합 테스트를 실행합니다.

```bash
./gradlew test \
  --tests '*NotificationSseIntegrationTest.deliversSavedNotificationToReceiverOnAnotherNode' \
  --tests '*NotificationSseIntegrationTest.replaysMissedNotificationsAfterReconnectToAnotherNode' \
  --tests '*NotificationSseIntegrationTest.closingOneConnectionPreservesOtherConnectionAndSharedHistory' \
  --tests '*NotificationSseIntegrationTest.keepsConnectionAndDeliversNewNotificationAfterHistoryExpires'
```

이 명령은 Testcontainers의 별도 PostgreSQL·Redis와 임의 HTTP 포트를 사용합니다.
위에서 띄운 Compose 서버나 로그인 계정을 대상으로 하는 명령은 아닙니다.
테스트의 A·B는 동일 JVM의 독립 Spring 컨텍스트입니다.
검증 결과와 한계는 [이력 테스트 가이드](12-history-testing.md)에 정리했습니다.

## 결과가 다를 때와 종료

| 증상 | 확인할 항목 |
| --- | --- |
| 로그인·회원가입 403 | CSRF 발급, 동일 쿠키 파일, 헤더 토큰 |
| SSE 401 | JWT 만료, Bearer 접두사, 두 서버의 JWT 설정 |
| SSE 503 | 초기 이력 조회 실패 원인, Redis·DB 상태 |
| 하트비트만 수신 | 실제 저장된 이력과 DB 알림 존재 여부, 채널·접두사 일치 |
| 자동 테스트 실패 | 테스트 보고서, Docker 상태, 실패 단계의 로그 |

PUBLISH의 반환 구독자 수는 브라우저 수신 성공 건수가 아닙니다.
검증을 마치면 두 curl을 Ctrl+C로 종료하고 [앱 종료 절차](07-redis-multi-node.md#검증과-종료)를 진행합니다.
임시 쿠키 파일도 사용 후 정리합니다.

---

[← 다중 서버 실행](07-redis-multi-node.md) · [목차](README.md) · [다음: 공유 이력 →](09-shared-history.md)
