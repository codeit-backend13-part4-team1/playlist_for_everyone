# 4. 테스트 읽기와 작성

## 테스트별 검증 범위

| 테스트 | 검증하는 것 |
| --- | --- |
| `RedisUserSessionRepositoryTest` | 실제 Redis에서 세션 교체·확인·연장·종료와 TTL 검증 |
| `JwtTokenProviderTest`, `JwtRefreshTokenProviderTest` | `sid` 포함 발급, `sid` 누락·형식 오류 토큰 거부 |
| `JwtAuthenticationSuccessHandlerTest` | 로그인 시 세션 시작, 중복 로그인 이벤트 발행, 조회 실패 시 세션 미교체 |
| `JwtAuthenticationFilterTest` | 무효 세션 401, 세션 확인 후 사용자 조회 순서 |
| `AuthServiceTest` | 무효 세션 재발급 거부, `sid` 유지, 세션 연장, 로그아웃 시 세션 종료 |
| `UserSessionRevokeListenerTest` | 권한 변경·잠금 시 세션 종료와 이벤트, 저장소 오류 비전파 |
| `UserAdministrationServiceIntegrationTest` | 커밋 후에만 세션 종료, 롤백·잠금 해제·같은 권한이면 유지 |

## 토큰이 필요한 테스트 작성

필터가 로그인 세션을 확인하므로, `jwtTokenProvider`로 토큰만 만들면 401이 발생합니다.

### 실제 필터를 거치는 통합 테스트

`TestTokens`로 세션을 시작한 뒤 토큰을 발급합니다.

```java
@Autowired
private UserSessionRepository userSessionRepository;

String token = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, userId);
```

Redis 컨테이너가 없는 테스트는 메모리 저장소를 사용합니다.

```java
@Import(InMemoryUserSessionConfig.class)
```

같은 사용자로 `TestTokens.accessToken()`을 다시 호출하면 이전 토큰은 무효가 됩니다(중복 로그인과 같은 동작).
한 테스트 안에서는 토큰을 한 번 만들어 재사용합니다.

### `SecurityConfig`를 import하는 `@WebMvcTest`

세션 저장소 Mock을 추가하고, 인증 성공을 만들 때는 `isCurrent`를 `true`로 설정합니다.
Mock의 기본값은 `false`이므로 설정하지 않으면 401이 발생합니다.

```java
@MockitoBean
private UserSessionRepository userSessionRepository;

given(jwtTokenProvider.parseAccess(TOKEN))
        .willReturn(new AccessTokenClaims(USER_ID, SESSION_ID));
given(userSessionRepository.isCurrent(USER_ID, SESSION_ID))
        .willReturn(true);
```

## 수동 확인

일반 창과 시크릿 창에서 같은 계정으로 로그인한 뒤, 먼저 로그인한 창에서 API를 호출하면 로그인 화면으로 이동합니다.
권한 변경·계정 잠금은 관리자 API로 변경한 뒤 대상 사용자의 다음 요청이 401인지 확인합니다.

Redis에서 세션을 직접 확인할 수 있습니다.

```bash
redis-cli KEYS 'auth:session:*'
redis-cli GET auth:session:<사용자 ID>
redis-cli TTL auth:session:<사용자 ID>
```