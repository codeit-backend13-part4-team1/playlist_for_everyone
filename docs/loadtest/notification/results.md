# 알림 비동기 적용 전 측정 결과

| 측정 조건 | HTTP 성공 / 실패 | HTTP p95 (ms) | 처리량 (요청/초) | 알림 저장 / SSE 발행 | 추가 확인 |
|---|---:|---:|---:|---:|---|
| 팔로워 1명 · 순차 | 100 / 0 | 109 | 11.90 | 100 / 100 (종료 누적) | |
| 팔로워 1명 · 동시 5개 | 100 / 0 | 290 | 53.13 | +100 / +100 | |
| 팔로워 10명 · 순차 | 100 / 0 | 208 | 10.24 | +1,000 / +1,000 | |
| 팔로워 10명 · 동시 5개 | 100 / 0 | 168 | 32.77 | +1,000 / +1,000 | |
| 팔로워 10명 · 동시 5개 · DB 풀 2개 | 10 / 15 | 11,849 | 0.46 | 1 / 1 (종료 누적) | 플레이리스트 커밋 10건, 알림 저장 실패 99건, DB 연결 획득 타임아웃 114건 |

세부 측정 기록을 함께 보관합니다. [측정 증빙](evidence/)

## Actuator 지표 조회

- 지표가 아직 등록되지 않은 상태에서 개별 지표를 조회하면 `404`가 반환됩니다. 관리자 토큰으로 `GET /actuator/metrics`를 먼저 호출해 `200` 응답과 등록된 지표 목록을 확인할 수 있습니다.
- 브라우저 주소창에서 직접 열면 `Authorization` 헤더가 생략되어 `401`이 반환됩니다. 로컬 `curl` 또는 JMeter에서 `Authorization: Bearer <관리자 accessToken>`을 넣어 조회합니다.

```bash
curl -i -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/actuator/metrics
```

## JMeter 파일

JMeter의 **Test Plan → User Defined Variables**에서 `ACCESS_TOKEN`에 관리자 계정의 토큰 원문을 입력합니다. JMeter가 `Bearer `를 붙입니다. `CSRF_TOKEN`에는 같은 세션의 `XSRF-TOKEN` 쿠키 값을 넣습니다.

플레이리스트 생성과 지표 조회에 같은 관리자 계정을 사용합니다. 테스트에 쓸 사용자가 이 관리자 계정을 팔로우하게 설정하고, 실행할 Thread Group만 활성화합니다.

- [notification-sequential.jmx](../../../loadtest/notification/notification-sequential.jmx)
- [notification-concurrent.jmx](../../../loadtest/notification/notification-concurrent.jmx)
- [notification-concurrent-small-connection.jmx](../../../loadtest/notification/notification-concurrent-small-connection.jmx)
