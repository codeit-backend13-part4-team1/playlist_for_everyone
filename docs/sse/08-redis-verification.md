# 8. Redis SSE 실제 수신 검증

목표는 **같은 사용자가 서로 다른 서버에 연결했을 때 한 번 발행한 이벤트를 두 연결에서 받는지** 확인하는 것입니다.
[다중 서버 실행](07-redis-multi-node.md)을 마치고 터미널 세 개를 준비합니다. 모든 명령은 프로젝트 루트 기준입니다.

이 절차는 Redis CLI 발행 이후 수신 리스너 → 로컬 전송 → 실제 HTTP 응답을 확인합니다.
실제 `RedisSseEventPublisher`를 통한 발행은 [Redis 통합 테스트](05-testing.md#redis-테스트의-범위)에서 검증합니다.
알림 DB 저장·커밋·생성 리스너까지 포함하는 #51 경로는 [알림 전송 통합 테스트](../notification/02-testing.md)로 확인합니다.

## 1. 검증용 사용자로 로그인

기존 로컬 검증용 계정이 있으면 로그인부터 진행합니다. 새 계정이 필요하면 아래 예시 값을 사용할 고유한 이메일·이름·비밀번호로 바꿔 생성합니다.

```bash
curl -i --max-time 10 -X POST http://localhost:8081/api/users \
  -H 'Content-Type: application/json' \
  --data '{"email":"sse-check@example.com","name":"SSE 검증 사용자","password":"local-sse-check"}'
```

생성 성공 응답은 `201`입니다. 로그인은 JSON 대신 폼 데이터로 이메일을 `username`에 전달합니다.

```bash
curl -i --max-time 10 -X POST http://localhost:8081/api/auth/sign-in \
  --data-urlencode 'username=sse-check@example.com' \
  --data-urlencode 'password=local-sse-check'
```

로그인 성공 응답 `200`의 JSON에서 다음 두 값을 확인합니다.

| 응답 필드 | 아래 예시에서 바꿀 값 |
| --- | --- |
| `accessToken` | `<ACCESS_TOKEN>` |
| `userDto.id` | `<USER_ID>` |

현재 브랜치는 로그인과 SSE 최초 요청에 JWT 인증을 사용하며 CSRF 검사는 비활성화되어 있습니다.
CSRF 정책이 적용되면 로그인 요청의 토큰 준비 절차도 함께 갱신해야 합니다. 실제 계정·토큰은 문서에 저장하지 않습니다.

## 2. 같은 토큰으로 두 서버에 연결

터미널 A에서 서버 A에 연결합니다. `<ACCESS_TOKEN>`은 꺾쇠까지 포함하여 로그인 응답 값으로 바꿉니다.

```bash
curl -i -N --max-time 120 http://localhost:8081/api/sse \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

터미널 B에서도 **같은 토큰**으로 서버 B에 연결합니다.

```bash
curl -i -N --max-time 120 http://localhost:8082/api/sse \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

두 응답의 `200`, `Content-Type: text/event-stream`과 최초 `:connected` 주석을 확인합니다.
기본 설정에서는 이후 약 15초 간격으로 `:heartbeat` 주석이 옵니다.
두 연결을 열어 둔 상태에서 다음 단계로 진행합니다. 120초 제한으로 curl이 종료되었다면 다시 연결합니다.

## 3. 샘플 이벤트 한 번 발행

터미널 C에서 `<USER_ID>`를 로그인 응답의 `userDto.id`로 바꾸고 실행합니다.
예시는 기본 채널을 사용합니다. 별도 채널을 설정했다면 `everyone:sse:events`도 그 값으로 바꿉니다.

```bash
docker compose --env-file .env.dev \
  -f compose.yaml -f compose.sse.yml \
  exec -T redis redis-cli -x PUBLISH everyone:sse:events <<'JSON'
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "receiverId": "<USER_ID>",
  "eventName": "notifications",
  "data": {"message": "두 서버 SSE 전송 확인"}
}
JSON
```

`redis-cli -x`는 표준 입력을 메시지로 읽습니다. 본문은 실제 알림 DTO가 아닌 공통 전송 검증용 데이터입니다.
검증을 반복할 때는 새 이벤트 UUID를 사용하여 이전 출력과 구분합니다.

## 4. 실제 응답과 비교

두 SSE 터미널에서 아래 프레임을 각각 확인합니다. `data`의 값은 문자열로 이중 인코딩된 JSON이 아닌 객체여야 합니다.

```text
id:550e8400-e29b-41d4-a716-446655440000
event:notifications
data:{"message":"두 서버 SSE 전송 확인"}

```

- 두 연결의 이벤트 ID, 이름, JSON 데이터가 발행 값과 일치해야 합니다.
- 한 번 발행한 이벤트가 각 연결에 한 번씩 나타나는지 확인합니다. 중간의 하트비트 주석은 별개입니다.
- Redis 명령의 응답 `2`는 구독자 수입니다. 다른 구독자가 있으면 값이 달라질 수 있으며, 이 숫자만으로 실제 클라이언트 수신을 판정하지 않습니다.

여기서 확인하는 것은 현재 연결에 대한 전달입니다. 중복이 보이지 않았다는 관찰만으로 모든 장애 상황의 정확히 한 번 전달을 보장하지 않습니다.
검증 후에는 `Ctrl+C`로 두 연결을 닫고 [앱 종료 절차](07-redis-multi-node.md#검증과-종료)를 진행합니다.

## 결과가 다를 때

| 증상 | 확인할 항목 |
| --- | --- |
| 연결이 거부됨 | 앱 기동 완료, `8081`·`8082` 포트, 컨테이너 로그 |
| SSE 연결이 `401` | 토큰 만료·복사 오류, `Bearer ` 접두사, 두 서버의 JWT 설정 |
| 하트비트만 오고 이벤트가 없음 | 로그인 사용자 ID와 `receiverId` 일치, 채널 일치, 두 서버의 자동 구독 활성화 |
| 이벤트를 건너뛰었다는 WARN | UUID·이벤트 이름·필수 필드와 [실패 로그](06-redis-transport.md#실패-처리와-로그) |

---

[← 이전: 다중 서버 실행](07-redis-multi-node.md) · [SSE 목차](README.md) · [관련: 알림 구현 가이드](../notification/README.md)
