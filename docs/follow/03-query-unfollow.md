# 3. 조회와 취소

내 팔로우 관계 조회는 요청자와 대상의 쌍을 찾습니다.
팔로워 수와 ID 목록은 대상을 기준으로 집계하며, 취소는 관계 자체의 ID를 사용합니다.

## 조회 결과가 없을 때

| 서비스 계약 | 조회 조건 | 관계가 없을 때 |
| --- | --- | --- |
| `findFollowDto(command)` | 요청자 ID + 대상 ID | `FollowNotFoundException`, HTTP 404 |
| `countFollowers(followeeId)` | 대상 ID | 숫자 0 |
| `findFollowerIds(followeeId)` | 대상 ID | 빈 목록 |

이 조회들은 대상 사용자의 존재 여부를 따로 확인하지 않습니다.
따라서 없는 대상 ID를 전달해도 위와 동일하게 관계 조회는 404, 개수는 0, ID 목록은 빈 목록입니다.
팔로워 수 HTTP 응답은 `{"count": 0}` 같은 객체가 아닌 숫자 `0`입니다.

## 다른 사용자의 관계가 섞이지 않는 이유

A와 C가 모두 B를 팔로우해도 내 관계 조회에는 두 ID를 함께 사용합니다.

```text
A의 요청: followeeId=B, followerId=A → A가 만든 관계
C의 요청: followeeId=B, followerId=C → C가 만든 관계
```

`followerId`는 컨트롤러가 Principal에서 추출합니다.
저장소도 `followee.id`와 `follower.id`를 모두 조건으로 사용합니다.
다른 사용자의 관계만 있는 경우에는 현재 요청자의 관계가 없는 것으로 처리합니다.

## 다른 도메인에서 팔로워 ID가 필요하면

내부 조회 계약인 `FollowService.findFollowerIds(followeeId)`를 사용할 수 있습니다.
`FollowReader`는 서비스 내부에서 저장소 조회를 묶은 컴포넌트입니다.

ID 조회는 현재 인증 사용자에 의존하지 않으며 공개 HTTP API도 아닙니다.
반환 목록에는 정렬·페이징 계약이 없습니다. 이 호출 자체가 알림을 생성하거나 이벤트를 발행하지도 않습니다.

## 취소 처리 순서

1. 컨트롤러가 경로의 `followId`와 Principal의 `followerId`로 `UnFollowCommand`를 만듭니다.
2. 메서드 보안 프록시가 `FollowGuard.canUnfollow()`를 실행합니다.
3. Guard가 허용하면 서비스의 트랜잭션 안에서 관계를 다시 조회하고 삭제합니다.
4. 정상 종료하면 컨트롤러가 본문 없는 204를 반환합니다.

| Guard가 조회한 상태 | Guard 판단 | 후속 동작 |
| --- | --- | --- |
| 본인의 관계 | 허용 | 서비스에서 관계 삭제 |
| 타인의 관계 | 거부 | 서비스 본문 실행 전 접근 거부, 공통 예외 처리로 403 |
| 이미 없어진 관계 | 허용 | 서비스가 정상 종료, 204 |

관계가 없을 때 허용하는 것은 반복 취소에도 같은 정상 응답을 제공하기 위한 정책입니다.
서비스 본문의 소유자 재확인은 잘못된 관계를 삭제하지 않기 위한 확인이며,
이 분기 자체가 403을 만드는 것은 아닙니다. 403은 앞선 메서드 인가 거부에서 발생합니다.

## 삭제 범위

`Follow`의 사용자 연관관계에는 삭제 cascade를 지정하지 않았습니다.
팔로우를 취소하면 관계 행만 삭제하고 두 사용자 행은 유지합니다.
반대로 사용자 자체를 삭제할 때의 연관 데이터 정리는 이 취소 기능의 범위가 아닙니다.

취소에서는 `UserFollowedEvent`를 발행하지 않습니다.
취소 후 다시 등록하면 새 관계 ID가 만들어지고 신규 생성 이벤트를 발행합니다.

## 검증 연결

JWT 통합 테스트에서 사용자별 관계 조회, 팔로워 수, 반복 취소 204와 타인 관계 취소 403을 확인했습니다.
내부 팔로워 ID 조회와 취소·재등록의 이벤트는 서비스 통합 테스트에서 확인합니다.
역할별 테스트와 실행 방법은 [테스트 가이드](05-testing.md)에서 확인합니다.

## 관련 코드

[조회 계약](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/follow/service/FollowService.java) ·
[저장소 조회 조건](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/follow/repository/FollowRepository.java) ·
[소유자 Guard](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/follow/security/FollowGuard.java) ·
[취소 서비스](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/follow/service/impl/FollowServiceImpl.java)

---

[← 등록과 트랜잭션](02-registration-transactions.md) · [목차](README.md) · [다음: 사용자 조회와 생성 이벤트 →](04-follow-event.md)
