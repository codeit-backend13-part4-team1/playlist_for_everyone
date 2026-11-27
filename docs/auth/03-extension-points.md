# 3. 로그인 세션 폐기와 실시간 연결 종료

새 로그인으로 세션을 교체하거나 관리자가 권한을 변경하거나 계정을 잠그면 인증 도메인이 `UserSessionRevokedEvent`를 발행합니다. 이벤트에는 사용자 ID, **폐기된** 로그인 세션 ID, 사유가 들어갑니다. 각 서버는 앞의 두 ID가 모두 일치하는 연결을 종료하므로 새 로그인으로 연 연결은 유지됩니다. [사유별 세션 처리](02-force-logout.md)

| 연결 | 서버가 폐기 이벤트를 처리하는 방법 |
| --- | --- |
| WebSocket | `everyone.websocket.event.multiple=true`이면 Redis 채널로 전파하고, 구독 중인 서버가 자기 서버의 세션을 찾아 종료합니다. `false`이면 이벤트를 받은 로컬 서버가 처리합니다. [다중 서버 설정](../conversation/10-websocket-settings.md#공통-websocket-이벤트와의-관계) |
| SSE | SSE 리스너가 전용 Redis 채널로 폐기 정보를 발행합니다. 각 구독 서버는 해당 로그인으로 등록한 탭별 연결을 정리합니다. [연결 생명주기](../sse/01-connection-lifecycle.md) |

SSE는 연결을 등록한 직후 Redis의 현재 로그인 세션을 다시 확인합니다. 폐기 신호를 놓친 경우에는 하트비트 점검에서 폐기를 확인해 연결을 정리합니다. WebSocket의 인증 자연 만료 처리 범위는 [WebSocket 보안 가이드](../conversation/12-websocket-security-errors.md#로그인-세션-무효화와-후속-범위)를 참고합니다.
