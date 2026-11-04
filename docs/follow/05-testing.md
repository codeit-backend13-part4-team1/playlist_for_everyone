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
| `FollowSecurityIntegrationTest` | 실제 JWT·필터·서비스·PostgreSQL을 연결한 등록·취소·조회, 인증·인가 오류, 동시 등록·이벤트 발행 | CSRF, 실제 알림·SSE 소비 |

메서드 인가 테스트의 Guard는 Mock입니다. Guard 자체의 소유자 판단은 별도 Guard 테스트와 서비스 통합 테스트에서 확인합니다.
서비스 통합 테스트는 일부 충돌 시나리오에서 `FollowSaver` Spy로 실행 순서를 조정합니다.

## 컨트롤러와 JWT 통합 테스트의 인증 준비

현재 `FollowControllerTest`는 `addFilters = false`이며 서비스를 Mock으로 사용합니다.
`setUpUserAuthentication()`은 `SecurityUser`를 SecurityContext에 넣어 컨트롤러가 요청자 ID를 받을 수 있게 합니다.
각 테스트 후에는 `clearSecurityContext()`로 정리합니다.

이 설정으로 JWT 유효성이나 미인증 HTTP 요청의 차단을 확인하는 것은 아닙니다.
서비스에서도 인증 여부를 별도로 검사하지 않으므로, 미인증 서비스 직접 호출을 거부한다는 테스트 계약은 두지 않습니다.

`FollowSecurityIntegrationTest`는 `JwtTokenProvider`로 실제 JWT를 발급하고,
MockMvc 요청의 `Authorization: Bearer <토큰>` 헤더로 전달합니다.
공통 보안 필터와 실제 서비스·PostgreSQL을 연결하여 정상 응답, 인증 실패 401, 취소 소유자 인가 실패 403을 확인합니다.

## JWT 통합 테스트 32개의 구성

| 기능 | 개수 | 주요 확인 사항 |
| --- | --- | --- |
| 등록·취소 | 6 | 신규 201·중복 200, 본인·타인·반복·없는 관계 취소, 관계와 사용자 보존 |
| 인증 실패 | 12 | API 4개 × JWT 누락·만료·변조의 401 응답 |
| 조회 | 9 | 본인 관계 분리, 관계 미존재·취소 후 404, 팔로워 수와 없는 대상의 0 |
| 조회 입력 검증 | 4 | 조회 API 2개 × 대상 ID 누락·UUID 형식 오류의 400 응답 |
| 동시 등록 | 1 | 201·200 응답에 같은 관계 ID 반환, 최종 관계 한 건 |

신규 등록과 인증 실패 등록 테스트에서는 `@RecordApplicationEvents`로 이벤트 값과 미발행 조건도 확인합니다.
이는 발행 기록이며, 커밋 후 수신 검증은 서비스 통합 테스트가 담당합니다. 자세한 구분은 [이벤트 가이드](04-follow-event.md)를 참고합니다.

## 실행 환경

프로젝트는 [테스트 설정](../../src/test/resources/application.properties)에서 `test` 프로필을 활성화합니다.
기본 테스트 DB는 H2이며, 세 PostgreSQL 테스트 클래스는 컨테이너의 접속 정보로 이를 덮어씁니다.
대상은 `FollowRepositoryTest`, `FollowServiceIntegrationTest`, `FollowSecurityIntegrationTest`입니다.
이때 Flyway를 켜고 `ddl-auto=validate`로 실제 스키마와 엔티티 매핑을 확인합니다.

PostgreSQL 테스트에는 실행 중인 Docker 엔진이 필요합니다.
`postgres:16` 컨테이너는 Testcontainers가 생성하며 개발용 Compose DB를 테스트 대상으로 지정하지 않습니다.
최초 이미지·의존성 다운로드에는 네트워크가 필요할 수 있습니다.

JWT 통합 테스트는 전체를 `@Transactional`로 감싸지 않고 사용자를 미리 저장합니다.
독립 저장 트랜잭션과 동시 요청에서 이 데이터를 조회하며, 테스트 후 관계 → 사용자 순서로 정리합니다.
토큰 도우미·동시 요청 제어의 구성 이유는 [테스트 코드 주석](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/follow/controller/FollowSecurityIntegrationTest.java)을 참고합니다.

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

실제 JWT·보안 필터부터 PostgreSQL까지 연결한 API 검증을 실행하려면:

```bash
./gradlew test --tests '*FollowSecurityIntegrationTest'
```

팔로우 테스트 전체를 확인하려면 Docker를 준비한 뒤 실행합니다.

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.domain.follow.*'
```

결과는 `build/reports/tests/test/index.html`에서 확인합니다.
명령은 실행 방법 안내이며, 현재 문서 작성으로 모든 테스트 통과를 보증하는 것은 아닙니다.
일부 테스트 실행으로 생성한 JaCoCo 리포트를 프로젝트 전체 커버리지로 해석하지 않습니다.

## #76 검증 현황

아래는 2026-11-04에 확인한 기존 테스트 실행·OpenAPI 조회·UI 화면 확인 기록입니다.
이번 주석·문서 정리는 이 기록을 반영하며, 테스트를 새로 실행한 결과는 아닙니다.

| 항목 | 상태 |
| --- | --- |
| JWT·소유자 인가·조회·동시 등록·이벤트 통합 테스트 32개 | 검증 완료 |
| 생성된 OpenAPI의 인증 요구사항·오류 설명·응답 형식 | 검증 완료 |
| Swagger HTML·JS·CSS 및 문서 연결 설정 응답 | 검증 완료 |
| 실제 Swagger UI 화면 표시 | 검증 완료 |
| CSRF 연동·회귀 검증 | [#114](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/114) 후속 작업 |

상세 완료 조건은 [#76](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/76)을 기준으로 확인합니다.
Swagger UI 화면은 사용자 확인과 브라우저 도구 확인을 통해 검토했습니다.
`bearerAuth`와 API 4개의 자물쇠 표시, 인증 객체 숨김, 오류 설명의 소유자 인가·CSRF 구분, 오류 응답 Schema를 확인했습니다.

---

[← 사용자 조회와 생성 이벤트](04-follow-event.md) · [목차](README.md) · [팀원 개발 가이드](../DEVELOPMENT.md)
