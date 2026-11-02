# 팔로우 구현 가이드

팔로우 요청의 흐름을 이해하고, 기능을 수정하거나 다른 도메인과 연결할 때 참고하는 문서입니다.
기존 Controller → Service → Repository 구조 안에서 조회·저장·사용자 조회 역할을 컴포넌트로 분리했습니다.

**기준:** 2026-11-03, #46·#47·#48의 구현과 현재 #76 브랜치의 Principal 연결을 포함합니다.
실제 JWT를 사용하는 팔로우 HTTP 통합 검증은 아직 남아 있습니다.

## 먼저 구분할 세 가지 ID

A가 B를 팔로우하면 다음과 같습니다.

| 이름 | 의미 | HTTP 입력의 출처 |
| --- | --- | --- |
| `followerId` | 팔로우한 사용자 A | 서버가 인증된 Principal에서 추출 |
| `followeeId` | 팔로우받은 사용자 B | 등록 본문 또는 조회 쿼리 |
| `followId` | A → B 관계 자체의 ID | 취소 경로 변수 |

A → B와 B → A는 다른 관계입니다. 같은 방향의 관계만 UNIQUE 제약으로 중복을 막습니다.

## 전체 구조

```text
HTTP 요청 → 공통 JWT 인증 → FollowController → FollowService
                                               ├─ FollowReader → FollowRepository
                                               ├─ FollowSaver  → FollowRepository
                                               │               → UserFollowedEvent 발행
                                               │   └─ FollowUserReader → UserService
                                               └─ 취소: FollowGuard → FollowRepository
```

취소 Guard는 서비스 본문 실행 전에 메서드 보안 프록시가 호출합니다.
이벤트 발행 이후 실제 알림을 만드는 소비자 연동은 별도 작업입니다.

## 추천 읽기 순서

| 순서 | 문서 | 답을 얻을 수 있는 질문 |
| --- | --- | --- |
| 1 | [API와 인증·인가](01-api-security.md) | 요청자 ID는 어디서 오고, 인증과 소유자 검사는 누가 하나요? |
| 2 | [등록과 트랜잭션](02-registration-transactions.md) | 동시에 같은 요청이 오면 어떻게 기존 관계를 반환하나요? |
| 3 | [조회와 취소](03-query-unfollow.md) | 조회 결과가 없거나 취소를 반복하면 어떻게 되나요? |
| 4 | [사용자 조회와 생성 이벤트](04-follow-event.md) | 이벤트는 언제 발행되며 알림과는 어떻게 연결되나요? |
| 5 | [테스트 읽기와 실행](05-testing.md) | Mock 테스트와 PostgreSQL 테스트가 각각 무엇을 확인하나요? |

## 현재 구현과 남은 검증

| 구분 | 현재 상태 |
| --- | --- |
| 등록·취소·조회 | 서비스·저장소·Guard 구현과 관련 테스트가 있음 |
| 요청자 연결 | 컨트롤러가 `SecurityUser`의 ID를 Command에 전달 |
| 인증·인가 경계 | 공통 필터가 인증, 서비스 Guard가 취소 소유자 판단 |
| HTTP 보안 통합 | 실제 JWT·필터·서비스·DB를 연결한 검증은 [#76](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/76)에서 진행 |
| Swagger | Principal 숨김 적용. Bearer 인증 설정, 설명 정리와 UI·OpenAPI 확인은 #76의 남은 작업 |
| CSRF | 현재 공통 설정에서 비활성화. 공통 기반은 [#35](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/35), 팔로우 연동·검증은 [#114](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/114) |
| 생성 이벤트 | 신규 저장 트랜잭션에서 발행. 테스트 전용 리스너로 커밋 후 처리를 관찰 |
| 알림 연동 | 팔로우 이벤트를 알림 생성으로 연결하는 작업은 [#45](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/45)의 범위 |

이 표는 현재 코드와 테스트의 역할을 설명합니다. 전체 빌드·실제 HTTP 통합 검증의 완료 기록은 아닙니다.
변경을 시작할 때는 [FollowService 계약](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/follow/service/FollowService.java)과 해당 주제의 테스트를 함께 확인합니다.

---

[프로젝트 README](../../README.md) · [팀원 개발 가이드](../DEVELOPMENT.md) · [다음: API와 인증·인가 →](01-api-security.md)
