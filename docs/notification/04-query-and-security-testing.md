# 4. 조회·인증 테스트와 Swagger 확인

조회·읽음 처리의 계약은 [API 흐름 문서](03-query-and-security.md)를 먼저 참고합니다.
테스트는 **요청 전달 → 서비스 판단 → DB 조회 → 동시 삭제 → JWT 요청 경로**로 나누어 읽습니다.

## 테스트별 역할

| 테스트 | 검증하는 것 | 포함하지 않는 것 |
| --- | --- | --- |
| [NotificationControllerTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/controller/NotificationControllerTest.java) | 요청 바인딩·검증, 응답 형식, Principal의 UUID를 서비스로 전달 | 필터 제외·서비스 Mock. 실제 JWT와 DB 검증 없음 |
| [NotificationServiceTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/service/NotificationServiceTest.java) | 조회 결과의 DTO·커서 구성, 본인·타인·미존재 알림별 삭제 호출 여부 | 저장소 Mock. 실제 잠금·DB 삭제·커밋 검증 없음 |
| [NotificationRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/repository/NotificationRepositoryTest.java) | PostgreSQL·Flyway·Querydsl로 수신자별 목록·개수, 커서 탐색, 미존재 알림의 빈 잠금 조회 결과 검증 | 서비스의 소유자 확인·동시 삭제·JWT 호출 없음 |
| [NotificationServiceIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/service/NotificationServiceIntegrationTest.java) | 실제 서비스·PostgreSQL과 두 트랜잭션으로 잠금 대기, 두 작업의 정상 완료와 최종 삭제 검증 | HTTP·JWT·CSRF·SSE 전송 검증 없음 |
| [NotificationSecurityIntegrationTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/domain/notification/controller/NotificationSecurityIntegrationTest.java) | 실제 JWT 필터·사용자 조회·서비스·PostgreSQL을 통한 조회·삭제 | MockMvc 사용. 실제 HTTP 서버·로그인 API·Redis 전송·CSRF 검증 없음 |

MVC 테스트는 `SecurityContextHolder`에 인증 객체를 직접 넣어 Principal 인자 주입을 준비하고, 실행 후 정리합니다.
이 테스트의 통과만으로 토큰 검증이 동작한다고 판단하지 않습니다.
저장소 테스트는 `@DataJpaTest`의 기본 트랜잭션 롤백을 사용하며, 커밋 후 이벤트 전송은 [생성·전송 테스트](02-testing.md)가 확인합니다.
저장소 테스트의 `deleteById()`와 `flush()`는 삭제 후 목록·개수·커서를 확인하기 위한 데이터 준비입니다.
삭제 허용 여부는 서비스 단위 테스트에서, 실제 잠금과 커밋 순서는 [동시 삭제 검증](05-concurrent-deletion-testing.md)에서 확인합니다.

## 어떤 결과를 확인하는가

| 영역 | 주요 확인 내용 |
| --- | --- |
| MVC 입력 | 커서 날짜, UUID, 필수값, 양수 limit, 지원하는 정렬 기준·방향과 400 응답 |
| MVC 전달 | 인증 사용자 ID와 조회 조건·삭제 ID 전달, 200·204·500 응답 형식 |
| 서비스 읽음 처리 | 본인 알림일 때만 삭제 호출, 타인·미존재 알림은 삭제 호출 없음 |
| 저장소 커서 | 양방향 정렬, 같은 생성시각의 ID 순서, `limit + 1`, 커서 대상 삭제 후 탐색 |
| 저장소 목록·개수 | 수신자별 조회 범위, 준비한 행을 삭제한 뒤 목록·전체 개수의 변화 |
| 저장소 잠금 조회 | 존재하지 않는 ID에 빈 Optional 반환 |
| 서비스 동시 삭제 | 두 번째 작업의 DB 잠금 대기, 첫 커밋 후 두 작업의 정상 완료, 최종 행 삭제 |
| JWT 정상 요청 | 본인 목록·전체 개수, 삭제 후 목록·개수 변화, 타인 데이터 보존 |
| JWT 반복·미존재·타인 삭제 | 모두 204·빈 본문, 삭제할 대상이 없으면 기존 데이터 유지 |
| JWT 없음·만료·변조 | GET·DELETE 모두 401, 공통 오류 필드 확인, DELETE의 대상 행 보존 |

개별 사례는 각 클래스의 한글 `@DisplayName`에서 확인합니다.
JWT 실패 테스트는 HTTP 상태만 확인하지 않고, DELETE 요청 전 준비한 알림이 DB에 남는지도 검사합니다.

## JWT 통합 테스트의 데이터와 토큰

기본 `test` 프로필에서 PostgreSQL 16 컨테이너를 실행하고 실제 Flyway 마이그레이션을 적용합니다.
`DynamicPropertySource`가 컨테이너의 URL·사용자명·비밀번호·드라이버를 주입합니다.

1. JDBC로 본인·다른 사용자 두 명을 준비합니다. 실제 JWT 필터의 사용자 조회와 알림 수신자 외래 키에 필요합니다.
2. 조회·삭제할 알림을 직접 저장하고 `JwtTokenProvider`로 토큰을 발급합니다. 생성 이벤트·로그인 API는 호출하지 않습니다.
3. Bearer 헤더를 넣어 MockMvc로 요청하고 응답과 DB 결과를 함께 확인합니다.
4. 커밋된 데이터는 해당 테스트 사용자의 알림 → 사용자 순서로 정리합니다.

테스트 전체에는 `@Transactional`을 붙이지 않습니다. 서비스의 트랜잭션 결과를 확인하고 직접 정리합니다.
고정된 알림 생성시각은 정렬 결과를 예측하기 위한 값이며, JWT의 발급·만료 시각과는 별개입니다.

만료 토큰은 실제 키·유효기간을 유지한 채 `Clock.fixed`로 발급 시각만 과거로 옮겨 대기 없이 만듭니다.
변조 토큰은 정상 토큰의 헤더·페이로드를 유지하고 서명만 변경합니다.
각 사례는 먼저 `ExpiredJwtException` 또는 `SignatureException`을 확인한 후 HTTP 요청 결과를 검증합니다.

## CSRF 임시 제외 범위

공통 `SecurityConfig`에는 CSRF 보호가 활성화되어 있으며, 실제 DELETE 요청도 검증 대상입니다.
현재 JWT 통합 테스트의 `CsrfDisabledTestConfig`는 **이 클래스의 MockMvc 요청에만**
`CsrfFilter.skipRequest`를 적용합니다. JWT 필터·사용자 조회·서비스·DB 검증은 유지합니다.
팀원의 모든 테스트에서 CSRF를 끄거나 실제 애플리케이션 보안 설정을 바꾸는 구성은 아닙니다.
따라서 위 표의 DELETE 204·401 검증 결과는 CSRF를 임시 제외한 JWT 검증 범위를 뜻합니다.

[#124](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/124)에서 실제 `XSRF-TOKEN` 쿠키와
`X-XSRF-TOKEN` 헤더를 준비하는 검증으로 전환하면서 내부 임시 설정과 클래스의 `@Import`를 제거합니다.
그 상태에서 실제 CSRF 실패 응답과 회귀 동작을 확인합니다.
현재 테스트의 통과를 CSRF 검증 완료로 해석하지 않습니다.

## 실행과 결과 확인

프로젝트 루트에서 MVC·서비스 단위 테스트를 실행할 때는 Docker가 필요하지 않습니다.

```bash
./gradlew test --tests '*NotificationControllerTest' \
  --tests '*NotificationServiceTest' --no-daemon --console=plain
```

저장소·서비스 통합·JWT 통합 테스트는 Docker를 실행한 뒤 확인합니다. PostgreSQL은 컨테이너가 준비하며,
로컬 DB·Redis·`.env.dev`는 필요하지 않습니다. 최초 실행에는 이미지 다운로드가 필요할 수 있습니다.

```bash
./gradlew test --tests '*NotificationRepositoryTest' \
  --tests '*NotificationServiceIntegrationTest' \
  --tests '*NotificationSecurityIntegrationTest' --no-daemon --console=plain
```

생성·전송을 포함한 알림 전체 테스트는 다음 명령으로 실행합니다. 이 경우 Redis 컨테이너도 사용합니다.

```bash
./gradlew test --tests 'com.codeit.sb13.playlist_for_everyone.domain.notification.*' \
  --no-daemon --console=plain
```

결과는 `build/reports/tests/test/index.html`과 `build/test-results/test/`에서 확인합니다.
실행별 성공 건수는 고정하지 않으며, 이 명령은 프로젝트 전체 테스트·커버리지 검증을 대체하지 않습니다.

## Swagger UI 수동 확인

애플리케이션 실행 후 `/swagger-ui/index.html`과 생성 문서 `/v3/api-docs`를 대조합니다.
프로필별 문서 접근 정책은 공통 보안 설정을 따릅니다. 다음 확인에는 실제 알림 삭제 요청을 실행할 필요가 없습니다.

1. GET·DELETE 모두 인증 표시가 있고, `bearerAuth`가 HTTP Bearer/JWT 스키마를 참조하는지 확인합니다.
2. GET에는 `cursor`, `idAfter`, `limit`, `sortDirection`, `sortBy`만 입력값으로 보이는지 확인합니다.
3. DELETE에는 경로의 `notificationId`만 있고, 두 API에 Principal·수신자 ID·요청 본문 입력이 없는지 확인합니다.
4. 두 API의 401 설명이 토큰 없음·만료·변조를 포함하고, 오류 스키마에 `exceptionName`, `message`, `details`가 있는지 확인합니다.

JWT 문서 표현은 이 절차로 확인하며, 별도 문서 자동 테스트는 두지 않습니다.
실제 CSRF 요청과 Swagger의 최종 CSRF 계약 검증은 #124에서 진행합니다.

---

[← 이전: 목록 조회·읽음 처리와 JWT 인증](03-query-and-security.md) · [알림 목차](README.md) · [다음: 동시 읽음 처리와 잠금 검증 →](05-concurrent-deletion-testing.md)
