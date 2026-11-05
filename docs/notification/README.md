# 알림 구현 가이드

**생성 요청 → 저장·커밋 → SSE 전송 → 목록 조회 → 읽음 처리**를 따라갈 수 있도록 정리한 가이드입니다.
공통 생성·전송뿐 아니라 [#38 조회·삭제](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/38),
[#51 SSE 연동](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/51),
[#67 JWT 인증 연동](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/67),
[#129 수신자 연관관계](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/129)의 현재 코드와 검증 범위를 설명합니다.

## 추천 읽기 순서

| 순서 | 문서 | 확인할 내용 |
| --- | --- | --- |
| 1 | [알림 생성·저장과 SSE 전송](01-creation-and-delivery.md) | 입력 검증·말줄임, 두 커밋 경계, 이벤트 ID·DTO, 실패 처리 |
| 2 | [알림 생성·전송 테스트](02-testing.md) | Mock과 실제 인프라의 차이, 커밋·롤백 검증, 서버 간 수신 |
| 3 | [목록 조회·읽음 처리와 JWT 인증](03-query-and-security.md) | 수신자 결정, 커서 요청·응답, 삭제·오류 정책 |
| 4 | [조회·인증 테스트와 Swagger 확인](04-query-and-security-testing.md) | MVC·서비스·저장소·통합 테스트의 역할, 실행 명령, CSRF 후속 범위 |
| 5 | [동시 읽음 처리와 잠금 검증](05-concurrent-deletion-testing.md) | 두 트랜잭션의 순서, 준비·커밋 신호, DB 잠금 대기와 종료 정리 |
| 6 | [수신자 연관관계와 사용자 조회](06-receiver-association.md) | User 연결, 조회 컴포넌트, 사용자 보존과 DTO 변환 시 SQL 검증 |

코드 안의 주석은 각 클래스의 역할과 구현 이유를 설명합니다.
문서는 전체 흐름과 테스트의 보장 범위를 연결하여 읽는 데 사용합니다.

조회 API는 3번부터, 생성 이벤트는 1번부터 읽으면 됩니다. 동시 삭제 테스트의 실행 순서는 5번에서 확인합니다.
수신자 매핑 변경과 사용자 도메인 호출 구조를 확인하려면 6번부터 읽으면 됩니다.

## 다른 영역과의 경계

| 알림 도메인에서 설명하는 것 | 공통 SSE 가이드에서 설명하는 것 |
| --- | --- |
| 생성 요청, 독립 저장·커밋, DTO 스냅샷 | 연결 생성·종료, 다중 탭, 하트비트 |
| 커밋 후 발행 요청, 저장·발행 실패 처리 | Redis 발행·수신, 로컬 연결로 전송 |
| 알림 생성부터 HTTP 수신까지의 통합 테스트 | 공통 전송 계약과 저장소·설정 테스트 |

알림은 `SseEventPublisher` 계약으로 전송을 요청합니다.
연결이나 브로커 동작이 궁금하면 [SSE 구현 가이드](../sse/README.md)를 이어서 읽습니다.
HTTP 인증은 공통 JWT 필터가 담당하고, 알림 컨트롤러는 검증된 사용자 ID를 조회·삭제 범위로 전달합니다.

## 현재 구현과 후속 범위

공통 생성 요청 → 알림 저장·커밋 → Redis → 수신자의 SSE 응답까지 연결되어 있습니다.
저장 시 사용자 조회 컴포넌트로 수신자를 찾아 `User` 연관관계로 연결하며, 요청·응답·이벤트에는 수신자 UUID를 사용합니다.
조회·읽음 처리는 인증된 사용자의 알림에 한정합니다.
목록 조회는 저장소의 수신자 조건으로 제한하고, 읽음 처리는 서비스가 잠금 조회 후 소유자를 확인하여 삭제합니다.
생성·전송 검증은 2번, 조회·삭제·JWT 검증은 4번, 동시 삭제 검증은 5번 문서에서 확인합니다.

업무별 이벤트를 공통 생성 요청으로 바꾸는 [#45](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/45)는 별도 작업입니다.
알림의 실제 CSRF 토큰 연동·검증과 테스트 임시 설정 제거는 [#124](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/124)에서 다룹니다.
DM 본문 전송 연동, Kafka 구현체, 공유 이벤트 이력·누락 복구와 Outbox도 현재 가이드의 구현 범위에 포함하지 않습니다.

---

[프로젝트 README](../../README.md) · [팀원 개발 가이드](../DEVELOPMENT.md) · [공통 SSE 가이드](../sse/README.md)
