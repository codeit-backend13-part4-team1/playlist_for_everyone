package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.controller.AuthController;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.controller.UserController;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserAdministrationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.PasswordEncoderConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityAuthenticationConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        // 실제 CSRF 토큰 발급 API를 테스트에 포함한다.
        controllers = {UserController.class, AuthController.class},
        properties = {
                // 기본값이 7일이 아니라 이 값을 성공 핸들러에 전달하는지 검증한다.
                "everyone.jwt.refresh-token-validity=3d",

                // 실제 SecurityConfig에서 쿠키 설정을 읽어 전달해야 한다.
                "everyone.jwt.refresh-cookie-secure=true"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        SecurityAuthenticationConfig.class,
        PasswordEncoderConfig.class,
        MDCLoggingInterceptor.class
})
@DisplayName("로그인 SecurityFilterChain 연결")
class LoginSecurityTest {


    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String REFRESH_TOKEN = "issued-refresh-token";

    private static final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(3);

    private static final String EMAIL = "test@gmail.com";
    private static final String PASSWORD = "test=password";
    private static final String ACCESS_TOKEN = "issued-access-token";

    private static final String CSRF_TOKEN_URL = "/api/auth/csrf-token";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoderConfig passwordEncoderConfig;

    // 사용자 조회 결과만 대신한다.
    // AuthenticationManager와 비밀번호 검증 Provider는 실제 객체다.
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // 실제 Redis 연결은 저장소 테스트에서 검증한다.
    // 여기서는 로그인 성공 후 저장 요청이 전달되는지 확인 한다.
    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private UserAdministrationService userAdministrationService;


    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // SecurityConfig에서 로그아웃 핸들러를 생성할 때 필요한 의존성이다.
    @MockitoBean
    private AuthService authService;

    // SecurityConfig가 로그인 세션 저장소를 주입받는다.
    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인하면 토큰과 사용자 정보를 반환한다")
    void 정상_로그인에_성공한다() throws Exception {
        givenLoginSuccess();

        MvcResult result = mockMvc.perform(signIn(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.userDto.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.email").value(EMAIL))
                .andExpect(jsonPath("$.userDto.name").value("테스트 사용자"))
                .andExpect(jsonPath("$.userDto.password").doesNotExist())
                .andReturn();

        // 성공 응답뿐 아니라 실제 인증 경로가 실행됐는지도 확인한다.
        verify(userDetailsService).loadUserByUsername(EMAIL);
        verify(jwtTokenProvider).createAccessToken(USER_ID);

        // SecurityConfig가 Refresh Token을 지원하는 성공 핸들러를
        // 실제 로그인 경로에 연결했는지 확인한다.
        verify(jwtTokenProvider).createRefreshToken(USER_ID);

        // 테스트 설정의 3일이 저장소 TTL로 전달되어야 한다.
        verify(refreshTokenRepository).save(
                USER_ID,
                REFRESH_TOKEN,
                REFRESH_TOKEN_EXPIRATION
        );

        // 다른 쿠키가 추가되더라도 Refresh Token 쿠키만 선택한다.
        var refreshCookies = result.getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(value -> value.startsWith("REFRESH_TOKEN="))
                .toList();

        assertThat(refreshCookies).hasSize(1);

        assertThat(refreshCookies.get(0))
                .startsWith("REFRESH_TOKEN=" + REFRESH_TOKEN + ";")
                .contains("HttpOnly")
                .contains("; Secure")
                .contains("SameSite=Lax")
                .contains("Max-Age=" + REFRESH_TOKEN_EXPIRATION.toSeconds())
                .contains("Path=/api/auth")
                .doesNotContain("Domain=");

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(REFRESH_TOKEN);

        assertThat(result.getResponse().getRedirectedUrl()).isNull();

        // 전체 로그인 흐름에서도 세션을 만들지 않아야 한다.
        assertThat(result.getRequest().getSession(false)).isNull();

        // 요청 종류 후 TreadLocal 인증 정보가 정리되어야 한다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401을 반환하고 토큰을 발급하지 않는다")
    void 잘못된_비밀번호로_로그인하면_실패한다() throws Exception {
        prepareSecurityUser(false);

        expectLoginFailure("wrong-password");
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 동일한 로그인 실패 응답을 반환한다")
    void 존재하지_않는_이메일로_로그인하면_실패한다() throws Exception {
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willThrow(new UsernameNotFoundException("사용자 없음"));

        expectLoginFailure(PASSWORD);
    }

    @Test
    @DisplayName("잠긴 사용자는 비밀번호가 맞아도 로그인할 수 없다")
    void 잠긴_사용자는_로그인할_수_없다() throws Exception {
        prepareSecurityUser(true);

        expectLoginFailure(PASSWORD);
    }

    @Test
    @DisplayName("로그인과 로그아웃 이후 CSRF 토큰을 다시 받아 재로그인한다")
    void 로그아웃_후_CSRF_토큰을_다시_받아_로그인한다() throws Exception {
        givenLoginSuccess();

        // 1. signIn() 내부에서 실제 CSRF 발급 API를 호출한 뒤 로그인한다.
        MvcResult loginResult = mockMvc.perform(signIn(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse()
                .getCookie("REFRESH_TOKEN");

        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isEqualTo(REFRESH_TOKEN);

        // 2. 로그인 이후 사용할 CSRF 토큰을 발급 API에서 다시 받는다.
        // 로그인 전의 토큰을 계속 사용할 수 있다고 가정하지 않는다.
        Cookie logoutCsrfCookie = issueCsrfCookie();

        // 3. 로그인 응답에서 받은 Refresh Token과 새 CSRF 토큰으로 로그아웃한다.
        MvcResult logoutResult = mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(
                                refreshCookie,
                                logoutCsrfCookie
                        )
                        .header(
                                CSRF_HEADER_NAME,
                                logoutCsrfCookie.getValue()
                        ))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(authService).logout(REFRESH_TOKEN);

        // 브라우저가 Refresh Token 쿠키를 삭제하도록 응답했는지 확인한다.
        Cookie deletedRefreshCookie = logoutResult.getResponse()
                .getCookie("REFRESH_TOKEN");

        assertThat(deletedRefreshCookie).isNotNull();
        assertThat(deletedRefreshCookie.getMaxAge()).isZero();
        assertThat(deletedRefreshCookie.getPath()).isEqualTo("/api/auth");

        // 4. 첫 로그인 성공 후 SecurityUser의 비밀번호가 지워질 수 있다.
        // 실제 사용자 조회처럼 비밀번호 해시를 가진 새 객체를 반환하도록 준비한다.
        prepareSecurityUser(false);

        // 5. signIn()이 CSRF 토큰을 다시 발급받고 두 번째 로그인을 요청한다.
        // 삭제된 Refresh Token 쿠키는 다시 보내지 않는다.
        MvcResult secondLoginResult = mockMvc.perform(signIn(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andReturn();

        Cookie secondRefreshCookie = secondLoginResult.getResponse()
                .getCookie("REFRESH_TOKEN");

        assertThat(secondRefreshCookie).isNotNull();
        assertThat(secondRefreshCookie.getValue()).isEqualTo(REFRESH_TOKEN);
        assertThat(secondRefreshCookie.getMaxAge()).isPositive();

        // 두 번 모두 실제 로그인 인증 과정과 토큰 저장 요청까지 도달해야 한다.
        verify(userDetailsService, times(2)).loadUserByUsername(EMAIL);
        verify(jwtTokenProvider, times(2)).createAccessToken(USER_ID);
        verify(jwtTokenProvider, times(2)).createRefreshToken(USER_ID);

        verify(refreshTokenRepository, times(2)).save(
                USER_ID,
                REFRESH_TOKEN,
                REFRESH_TOKEN_EXPIRATION
        );

        // 재로그인 후에도 서버 세션에 인증 정보를 보관하지 않는다.
        assertThat(secondLoginResult.getRequest().getSession(false)).isNull();
    }

    private void prepareSecurityUser(boolean locked) {
        SecurityUser principal = new SecurityUser(
                USER_ID,
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                Role.USER,
                locked
        );

        // 실제 Provider가 입력 비밀번호와 이 해시를 비교한다.
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willReturn(principal);
    }

    @Test
    @DisplayName("실제 로그인 필터 경로에서도 프로필 이미지 URL을 반환한다")
    void 로그인_필터를_통해_프로필_이미지_URL을_반환한다() throws Exception {
        String profileImageUrl =
                "https://example.com/profile.png?signature=test";

        givenLoginSuccess(profileImageUrl);

        // signIn()은 실제 CSRF 발급 API를 호출한 뒤 로그인 요청을 만든다.
        mockMvc.perform(signIn(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.userDto.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.profileImageUrl")
                        .value(profileImageUrl))
                .andExpect(jsonPath("$.userDto.password").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        // SecurityConfig에 연결된 성공 핸들러가 DTO 조회 경로를 사용하는지 확인한다.
        verify(userService).getUserDtoById(USER_ID);
        verify(refreshTokenRepository).save(
                USER_ID,
                REFRESH_TOKEN,
                REFRESH_TOKEN_EXPIRATION
        );
    }

    private MockHttpServletRequestBuilder signIn(String password) throws Exception {

        Cookie csrfCookie = issueCsrfCookie();

        return post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", password)
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue());
    }

    private void expectLoginFailure(String password) throws Exception {
        MvcResult result = mockMvc.perform(signIn(password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exceptionName")
                        .value("BadCredentialsException"))
                .andExpect(jsonPath("$.message")
                        .value("로그인에 실패했습니다."))
                .andReturn();

        // 단순히 보호 경로라서 401이 나온 것을 성공으로 착각하지 않도록,
        // 로그인 과정에서 사용자 조회가 실행됐는지 함께 확인한다.
        verify(userDetailsService).loadUserByUsername(EMAIL);

        // 인증 실패 후 성공 핸들러의 사용자 조회·토큰 발급은 실행되지 않는다.
        verifyNoInteractions(userService, jwtTokenProvider, refreshTokenRepository);

        assertThat(result.getResponse().getRedirectedUrl()).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("REFRESH_TOKEN="));
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

    }

    /**
     * 로그인 전에 서버가 발급한 CSRF 쿠키를 받는다.
     *
     * 토큰 생성과 쿠키 저장은 실제 SecurityFilterChain이 처리한다.
     */
    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);

        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();

        return csrfCookie;
    }

    /**
     * 로그인 성공에 필요한 사용자 조회 결과와 토큰 발급 결과를 준비한다.
     *
     * AuthenticationManager와 비밀번호 검증은 실제 객체가 처리한다.
     */
    /**
     * 기존 로그인 테스트는 이미지가 없는 사용자를 기본으로 사용한다.
     */
    private void givenLoginSuccess() {
        givenLoginSuccess(null);
    }

    /**
     * 실제 인증 과정에 사용할 principal과 성공 응답용 DTO를 준비한다.
     * 비밀번호 검증은 실제 AuthenticationManager와 PasswordEncoder가 수행한다.
     */
    private void givenLoginSuccess(String profileImageUrl) {
        prepareSecurityUser(false);

        UserDto userDto = new UserDto(
                USER_ID,
                LocalDateTime.of(2026, 10, 28, 9, 0),
                EMAIL,
                "테스트 사용자",
                profileImageUrl,
                Role.USER,
                false
        );

        given(userService.getUserDtoById(USER_ID))
                .willReturn(userDto);
        given(jwtTokenProvider.createAccessToken(USER_ID))
                .willReturn(ACCESS_TOKEN);
        given(jwtTokenProvider.createRefreshToken(USER_ID))
                .willReturn(REFRESH_TOKEN);
    }

}
