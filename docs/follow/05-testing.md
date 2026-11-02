# 5. 테스트 읽기와 실행

테스트 이름보다 실행하는 경계를 먼저 확인합니다.
Mock으로 확인한 정책, 실제 DB 제약조건, 실제 JWT HTTP 요청은 각각 다른 검증입니다.

## 테스트별 역할

테스트 코드는 [팔로우 테스트 디렉터리](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/follow)에 있습니다.

| 테스트 | 확인하는 범위 | 포함하지 않는 범위 |
| --- | --- | --- |
| `FollowServiceImplTest` | 자기 팔로우, 중복 반환, 원인 예외 분류, 조회·취소 정책 | 실제 DB, 트랜잭션·인가 프록시 |
| `FollowSaverTest` | 사용자 조회, 저장 의뢰, 이벤트 값과 발행 호출 | 실제 커밋, 커밋 후 소비 |
| `FollowGuardTest` | 본인·타인·없는 관계의 허용 여부 | JWT, 메서드 보안 프록시 |
| `FollowMethodSecurityTest` | Guard 결과에 따른 프록시의 접근 허용·거부 | 실제 JWT와 HTTP, 실제 저장소 |
| `FollowControllerTest` | MVC 입력 검증, Principal ID 전달, 상태·본문·공통 오류 | 보안 필터, 실제 서비스·DB |
| `FollowRepositoryTest` | PostgreSQL 매핑·쿼리·UNIQUE·FK·NOT NULL, 사용자 보존 | 서비스 정책, HTTP 보안 |
| `FollowServiceIntegrationTest` | 실제 서비스·DB의 중복·동시 등록·복구·조회·취소·커밋 이벤트 | 실제 JWT HTTP 요청, 운영 알림·SSE 소비 |

메서드 인가 테스트의 Guard는 Mock입니다. Guard 자체의 소유자 판단은 별도 Guard 테스트와 서비스 통합 테스트에서 확인합니다.
서비스 통합 테스트는 일부 충돌 시나리오에서 `FollowSaver` Spy로 실행 순서를 조정합니다.

## 컨트롤러 테스트의 인증 준비

현재 `FollowControllerTest`는 `addFilters = false`이며 서비스를 Mock으로 사용합니다.
`setUpUserAuthentication()`은 `SecurityUser`를 SecurityContext에 넣어 컨트롤러가 요청자 ID를 받을 수 있게 합니다.
각 테스트 후에는 `clearSecurityContext()`로 정리합니다.

이 설정으로 JWT 유효성이나 미인증 HTTP 요청의 차단을 확인하는 것은 아닙니다.
서비스에서도 인증 여부를 별도로 검사하지 않으므로, 미인증 서비스 직접 호출을 거부한다는 테스트 계약은 두지 않습니다.
실제 401 응답은 공통 보안 필터를 거친 요청으로 검증해야 합니다.

## 실행 환경

프로젝트는 [테스트 설정](../../src/test/resources/application.properties)에서 `test` 프로필을 활성화합니다.
기본 테스트 DB는 H2이며, 두 PostgreSQL 테스트는 컨테이너의 접속 정보로 이를 덮어씁니다.
이때 Flyway를 켜고 `ddl-auto=validate`로 실제 스키마와 엔티티 매핑을 확인합니다.

PostgreSQL 테스트에는 실행 중인 Docker 엔진이 필요합니다.
`postgres:16` 컨테이너는 Testcontainers가 생성하며 개발용 Compose DB를 테스트 대상으로 지정하지 않습니다.
최초 이미지·의존성 다운로드에는 네트워크가 필요할 수 있습니다.

## 실행 명령

아래 명령은 모두 프로젝트 루트에서 실행합니다.
컨트롤러와 메서드 인가 테스트만 확인하려면:

```bash
./gradlew test --tests '*FollowControllerTest' --tests '*FollowMethodSecurityTest'
```

실제 PostgreSQL 저장소·서비스 경계를 확인하려면:

```bash
./gradlew test --tests '*FollowRepositoryTest' --tests '*FollowServiceIntegrationTest'
```

팔로우 테스트 전체를 확인하려면 Docker를 준비한 뒤 실행합니다.

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.domain.follow.*'
```

결과는 `build/reports/tests/test/index.html`에서 확인합니다.
명령은 실행 방법 안내이며, 현재 문서 작성으로 모든 테스트 통과를 보증하는 것은 아닙니다.
일부 테스트 실행으로 생성한 JaCoCo 리포트를 프로젝트 전체 커버리지로 해석하지 않습니다.

## #76에서 남은 검증

- 실제 JWT·공통 보안 필터·서비스·PostgreSQL을 연결한 신규·중복 등록과 소유자별 취소.
- 4개 API의 미인증·만료·변조 JWT 401, 타인 관계 취소 403과 공통 오류 응답.
- 사용자별 관계 조회 분리, 없는 관계의 404, 숫자형 팔로워 수와 입력 오류.
- 동시 인증 HTTP 요청의 동일 관계 반환·DB 한 행 유지.
- 실제 등록 요청의 이벤트에 담긴 `followerId`·`followeeId`·사용자 이름과 발행 조건.
- Swagger의 인증 요구사항·응답 설명과 실제 응답 일치, UI·OpenAPI 확인.

상세 완료 조건은 [#76](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/76)을 기준으로 확인합니다.
JWT 통합 테스트에서는 실제 공통 보안 설정을 사용하며, 인증정보를 대체한 테스트만으로 완료하지 않습니다.
CSRF 연동·회귀 검증은 [#114](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/114)의 범위입니다.

---

[← 사용자 조회와 생성 이벤트](04-follow-event.md) · [목차](README.md) · [팀원 개발 가이드](../DEVELOPMENT.md)
