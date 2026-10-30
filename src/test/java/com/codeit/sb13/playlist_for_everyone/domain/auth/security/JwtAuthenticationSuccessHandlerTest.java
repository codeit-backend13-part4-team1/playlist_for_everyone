package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 성공 처리")
class JwtAuthenticationSuccessHandlerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    // JWT의 실제 검증은 JwtTokenProviderTest에서 수행한다.
    // 여기서는 발급된 문자열 저장소와 쿠키에 전달되는지를 검증한다.
    private static final String REFRESH_TOKEN = "test-refresh-token";

    // 7일을 코드에 고정하지 않고 전달받은 기간을 사용하는지 확인 한다.
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(3);


    private static final String EMAIL = "test@gmail.com";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ENCODED_PASSWORD = "encoded_password";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JsonMapper objectMapper;
    private JwtAuthenticationSuccessHandler successHandler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // JSON 직렬화는 실제 객체로 검증한다.
        objectMapper = JsonMapper.builder().build();

        successHandler = new JwtAuthenticationSuccessHandler(
                jwtTokenProvider,
                userService,
                objectMapper
        );

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("인증 사용자에게 Access Token과 사용자 정보를 JSON으로 반환한다")
    void 로그인_성공_응답을_반환한다() throws Exception {
        Authentication authentication = prepareAuthentication();

        successHandler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        assertThat(response.getStatus()).isEqualTo(200);

        // charset이 붙더라도 application/json인지 확인한다.
        assertThat(response.getContentType()).isNotNull();
        assertThat(MediaType.parseMediaType(response.getContentType())
                .isCompatibleWith(MediaType.APPLICATION_JSON))
                .isTrue();

        assertThat(response.getCharacterEncoding())
                .isEqualToIgnoringCase(StandardCharsets.UTF_8.name());

        JsonNode body = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));

        assertThat(body.path("accessToken").asString())
                .isEqualTo(ACCESS_TOKEN);

        JsonNode userDto = body.path("userDto");

        assertThat(userDto.path("id").asString())
                .isEqualTo(USER_ID.toString());
        assertThat(userDto.path("email").asString())
                .isEqualTo(EMAIL);
        assertThat(userDto.path("name").asString())
                .isEqualTo("테스트 사용자");
        assertThat(userDto.path("role").asString())
                .isEqualTo("USER");

        // 필드 누락도 실패하도록 값뿐 아니라 존재 여부도 검사한다.
        assertThat(userDto.has("locked"))
                .isTrue();
        assertThat(userDto.path("locked").asBoolean())
                .isFalse();

        assertThat(userDto.has("createdAt")).isTrue();
        assertThat(userDto.path("createdAt").isNull()).isFalse();

        // 현재 이미지가 없는 사용자는 null을 반환한다.
        assertThat(userDto.has("profileImageUrl")).isTrue();
        assertThat(userDto.path("profileImageUrl").isNull()).isTrue();

        // 인증된 사용자의 ID로 조회하고 토큰을 발급해야 한다.
        verify(userService).getById(USER_ID);
        verify(jwtTokenProvider).createAccessToken(USER_ID);
    }

    @Test
    @DisplayName("응답에 비밀번호를 노출하지 않고 리다이렉트나 세션을 생성하지 않는다.")
    void 비밀번호_세션을_응답에_남기지_않는다() throws Exception {

        Authentication authentication = prepareAuthentication();

        successHandler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        String content = response.getContentAsString(
                StandardCharsets.UTF_8
        );

        JsonNode body = objectMapper.readTree(content);

        assertThat(body.has("password")).isFalse();
        assertThat(body.path("userDto").has("password")).isFalse();
        assertThat(content).doesNotContain(ENCODED_PASSWORD);

        // REST 로그인 성공 응답이므로 다른 페이지로 이동시키지 않는다.
        assertThat(response.getRedirectedUrl()).isNull();
        // 핸들러가 인증 정보를 보관할 세션을 만들지 않아야 한다.
        assertThat(request.getSession(false)).isNull();

    }


    @Test
    @DisplayName("로그인 성공 시 Refresh Token을 발급하고 유효기간을 함께 저장한다.")
    void 리프레시_토큰을_발급하고_저장한다() throws Exception {
        Authentication authentication = prepareAuthentication();

        given(jwtTokenProvider.createRefreshToken(USER_ID))
                .willReturn(REFRESH_TOKEN);

        JwtAuthenticationSuccessHandler handler = createRefreshSuccessHandler(true);

        handler.onAuthenticationSuccess(request, response, authentication);

        // 요청 파라미터가 아니라 인증된 사용자의 ID로 발급한다.
        verify(jwtTokenProvider).createRefreshToken(USER_ID);

        // 저장소에는 원본 토큰을 전달한다.
        // SHA-256 변환은 Redis 저장소 구현체의 책임이다.
        verify(refreshTokenRepository).save(
                USER_ID,
                REFRESH_TOKEN,
                REFRESH_TOKEN_VALIDITY
        );

        // 기존 로그인 응답 계약은 유지한다.
        assertThat(response.getStatus()).isEqualTo(200);

        String content = response.getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(content);

        assertThat(body.path("accessToken").asString())
                .isEqualTo(ACCESS_TOKEN);

        assertThat(body.path("userDto").path("id").asString())
                .isEqualTo(USER_ID.toString());

        // Refresh Token은 응답 본문에 넣지 않고 쿠키로 전달한다
        assertThat(body.has("refreshToken")).isFalse();
        assertThat(content).doesNotContain(REFRESH_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Refresh Token 쿠키에 보안 속성과 설정된 만료기간을 적용한다.")
    void 리프레시_토큰_쿠키를_전달한다(boolean secure) throws Exception {
        Authentication authentication = prepareAuthentication();

        given(jwtTokenProvider.createRefreshToken(USER_ID))
                .willReturn(REFRESH_TOKEN);

        JwtAuthenticationSuccessHandler handler = createRefreshSuccessHandler(secure);

        handler.onAuthenticationSuccess(request, response, authentication);

        // 실제 HTTP 응답에 실리는 Set-Cookie 헤더를 검증한다.
        // 다른 종류의 쿠키가 추가되더라도 Refresh Token 쿠키만 선택한다.
        var refreshToken = response.getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(value -> value.startsWith("REFRESH_TOKEN="))
                .toList();

        assertThat(refreshToken).hasSize(1);

        String cookie = refreshToken.get(0);

        assertThat(cookie)
                .startsWith("REFRESH_TOKEN=" + REFRESH_TOKEN + ";")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/api/auth")
                .contains("Max-Age=" + REFRESH_TOKEN_VALIDITY.toSeconds());

        // 운영 HTTPS용 설정에서는 Secure를 켜고,
        // 로컬 HTTP용 설정에서는 끌 수 있어야 한다.
        if (secure) {
            assertThat(cookie).contains("; Secure");
        } else {
            assertThat(cookie).doesNotContain("; Secure");
        }

        // Domain을 지정하지 않아 응답을 받은 호스트에 한정한다.
        assertThat(cookie).doesNotContain("Domain");

    }

    @Test
    @DisplayName("Refresh Token 저장에 실패하면 쿠키와 성공 응답 본문을 전달하지 않는다.")
    void 저장_실패시_토큰_응답을_전달하지_않는다() throws Exception {
        User user = User.builder()
                .email(EMAIL)
                .name("테스트 사용자")
                .password(ENCODED_PASSWORD)
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 10, 27, 12, 0));

        given(userService.getById(USER_ID)).willReturn(user);
        given(jwtTokenProvider.createRefreshToken(USER_ID))
                .willReturn(REFRESH_TOKEN);

        SecurityUser principal = new SecurityUser(
                USER_ID,
                EMAIL,
                ENCODED_PASSWORD,
                Role.USER,
                false
        );
        principal.eraseCredentials();

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );

        RedisConnectionFailureException failure = new RedisConnectionFailureException("테스트용 Redis 연결 실패");

        // Redis 장애가 발생한 상황을 재현한다.
        doThrow(failure)
                .when(refreshTokenRepository)
                .save(USER_ID, REFRESH_TOKEN, REFRESH_TOKEN_VALIDITY);


        JwtAuthenticationSuccessHandler handler = createRefreshSuccessHandler(true);

        assertThatThrownBy(()-> handler.onAuthenticationSuccess(request, response, authentication))
                .isSameAs(failure);

        // 저장 실패 전에 토큰 쿠키나 성공 본문을 작성하면 안 된다.
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .isEmpty();
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.isCommitted()).isFalse();
    }


    private Authentication prepareAuthentication() {
        User user = User.builder()
                .email(EMAIL)
                .name("테스트 사용자")
                .password(ENCODED_PASSWORD)
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(
                user,
                "createdAt",
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );

        given(userService.getById(USER_ID)).willReturn(user);
        given(jwtTokenProvider.createAccessToken(USER_ID))
                .willReturn(ACCESS_TOKEN);

        SecurityUser principal = new SecurityUser(
                USER_ID,
                EMAIL,
                ENCODED_PASSWORD,
                Role.USER,
                false
        );

        // AuthenticationManager의 인증 성공 이후 상태를 재현한다.
        principal.eraseCredentials();

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    /**
     * 테스트마다 쿠키의 Secure 정책을 명시해서 핸들러로 만든다.
     *
     * 실제 서버에서는 설정값을 연결할 예정
     */
    private JwtAuthenticationSuccessHandler createRefreshSuccessHandler(boolean secure) {
        return new JwtAuthenticationSuccessHandler(
                jwtTokenProvider,
                userService,
                objectMapper,
                refreshTokenRepository,
                REFRESH_TOKEN_VALIDITY,
                secure
        );
    }
}