package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.controller.AuthController;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.controller.UserController;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.authentication.AuthenticationManager;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        AuthController.class,
        UserController.class
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("CSRF 토큰 발급 API 보안 연결")
class CsrfSecurityTest {

    private static final String CSRF_TOKEN_URL = "/api/auth/csrf-token";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String REFRESH_URL = "/api/auth/refresh";

    // 실제 JWT 검증은 서비스 테스트에서 확인한다.
    // 여기서는 보안 필터와 컨트롤러 연결을 검증하므로 테스트 문자열을 사용한다.
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";

    @Autowired
    private MockMvc mockMvc;

    // 실제 SecurityFilterChain은 사용한다.
    // 아래 객체들은 보안 설정을 생성하는 데 필요한 의존성이다.
    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("로그인하지 않아도 CSRF 토큰 발급 API를 호출할 수 있다.")
    void 비로그인_사용자도_CSRF_토큰을_발급받는다() throws Exception {
        // 로그인 Principal, Authorization 헤더, 기존 쿠키 없이 호출한다.
        // CSRF 토큰은 로그인 요청을 보내기 전에도 받을 수 있어야 한다.
        mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // CSRF 토큰 발급에는 사용자 조회나 JWT 발급이 필요하지 않다.
        verifyNoInteractions(
                authenticationManager,
                jwtTokenProvider,
                userService,
                refreshTokenRepository,
                authService
        );

    }


    @Test
    @DisplayName("프론트에서 읽을 수 있는 XSRF-TOKEN 쿠키를 발급한다.")
    void CSRF_토큰을_읽을_수_있는_쿠키로_전달한다() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);

        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();

        // 프론트가 쿠키를 읽어 X-XSRF-TOKEN 헤더에 넣어야 한다.
        // 따라서 CSRF 쿠키는 JavaScript에서 읽을 수 있어야 한다.
        assertThat(csrfCookie.isHttpOnly()).isFalse();

        // 프론트 페이지와 다른 API 경로에서도 사용할 수 있도록 한다.
        assertThat(csrfCookie.getPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("CSRF 토큰 발급 시 HTTP 세션과 Refresh Token을 만들지 않는다")
    void 인증_세션과_Refresh_Token을_생성하지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andReturn();

        // CSRF 토큰을 서버의 HTTP 세션에 저장하지 않는다.
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID"))
                .isNull();

        // CSRF 토큰 발급은 로그인이나 로그인 유지 토큰 발급이 아니다.
        assertThat(result.getResponse().getCookie("REFRESH_TOKEN"))
                .isNull();

        verifyNoInteractions(refreshTokenRepository, authService);
    }


    @Test
    @DisplayName("CSRF 쿠키와 헤더가 일치하면 토큰 재발급을 허용한다")
    void CSRF_쿠키와_헤더가_일치하면_재발급한다() throws Exception {
        // 서버에서 실제로 발급한 CSRF 쿠키를 받는다.
        Cookie csrfCookie = issueCsrfCookie();

        // 보안 검증을 통과한 뒤 서비스가 정상 응답하는 상황을 준비한다.
        givenReissueSuccess();

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie("REFRESH_TOKEN", OLD_REFRESH_TOKEN)
                        )
                        // 프론트가 쿠키에서 읽은 원문을 그대로 헤더에 넣는 상황이다.
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(NEW_ACCESS_TOKEN));

        // 상태 코드뿐 아니라 실제 재발급 서비스까지 도달했는지 확인한다.
        verify(authService).reissue(OLD_REFRESH_TOKEN);
    }

    @ParameterizedTest(name = "{0}: CSRF 헤더가 없으면 403")
    @ValueSource(strings = {
            "/api/auth/sign-in",
            "/api/auth/refresh",
            "/api/auth/sign-out"
    })
    @DisplayName("인증 관련 POST 요청도 CSRF 헤더가 없으면 거절한다")
    void CSRF_헤더가_없으면_거절한다(String path) throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        // 브라우저가 쿠키만 자동으로 보낸 상황을 재현한다.
        // Refresh Token은 제공하되 CSRF 헤더는 넣지 않는다.
        mockMvc.perform(post(path)
                        .param("username", "user@gmail.com")
                        .param("password", "password123")
                        .cookie(csrfCookie, new Cookie("REFRESH_TOKEN", OLD_REFRESH_TOKEN)))
                .andExpect(status().isForbidden());

        // 로그인 인증아나 토큰 재발급, 로그아웃 처리 전에 차단되어야 한다.
        verifyNoInteractions(
                authenticationManager,
                authService,
                refreshTokenRepository
        );
    }

    @ParameterizedTest(name = "{0}: CSRF 쿠기가 없으면 403")
    @ValueSource(strings = {
            "/api/auth/sign-in",
            "/api/auth/refresh",
            "/api/auth/sign-out"
    })
    @DisplayName("CSRF 헤더가 있어도 비교할 쿠키가 없으면 거절한다")
    void CSRF_쿠키가_없으면_거절한다(String path) throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        // 토큰 값을 헤더에만 넣고 XSRF-TOKEN 쿠키는 보내지 않는다.
        // MockMvc는 이전 응답의 쿠키를 다음 요청에 자동으로 전달하지 않는다.
        mockMvc.perform(post(path)
                        .param("username", "user@gmail.com")
                        .param("password", "password123!")
                        .cookie(new Cookie("REFRESH_TOKEN", OLD_REFRESH_TOKEN))
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(
                authenticationManager,
                authService,
                refreshTokenRepository
        );
    }

    @ParameterizedTest(name = "{0}: CSRF 쿠키와 헤더가 다르면 403")
    @ValueSource(strings = {
            "/api/auth/sign-in",
            "/api/auth/refresh",
            "/api/auth/sign-out"
    })
    @DisplayName("CSRF 쿠키와 헤더의 토큰이 다르면 거절한다")
    void CSRF_쿠키와_헤더가_다르면_거절한다(String path) throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        // 쿠키는 서버가 발급한 값이고, 헤더만 다른 값으로 변경한다.
        String differentToken = csrfCookie.getValue() + "-changed";

        mockMvc.perform(post(path)
                        .param("username", "user@gmail.com")
                        .param("password", "password123!")
                        .cookie(
                                csrfCookie,
                                new Cookie("REFRESH_TOKEN", OLD_REFRESH_TOKEN)
                        )
                        .header(CSRF_HEADER_NAME, differentToken))
                .andExpect(status().isForbidden());

        verifyNoInteractions(
                authenticationManager,
                authService,
                refreshTokenRepository
        );
    }


    @Test
    @DisplayName("실제 CSRF 쿠키와 헤더가 일치하면 로그아웃한다")
    void CSRF_검증을_통과하면_로그아웃한다() throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        // 테스트용 csrf() 대신 서버에서 받은 실제 쿠키와 헤더를 사용한다.
        MvcResult result = mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(
                                csrfCookie,
                                new Cookie("REFRESH_TOKEN", OLD_REFRESH_TOKEN)
                        )
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        // CSRF 검증을 통과한 뒤 로그아웃 서비스가 실행되어야 한다.
        verify(authService).logout(OLD_REFRESH_TOKEN);

        // 로그아웃 성공 시 Refresh Token 쿠키를 삭제하도록 응답한다.
        Cookie deletedRefreshCookie =
                result.getResponse().getCookie("REFRESH_TOKEN");

        assertThat(deletedRefreshCookie).isNotNull();
        assertThat(deletedRefreshCookie.getMaxAge()).isZero();
        assertThat(deletedRefreshCookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    @DisplayName("회원가입 요청에 CSRF 헤더가 없으면 403을 반환하고 사용자를 생성하지 않는다.")
    void 회원가입_요청에_CSRF_헤더가_없으면_거절한다() throws Exception {
        // 실제 발급 API에서 받은 쿠키를 사용한다.
        Cookie csrfCookie = issueCsrfCookie();

        // 입력값은 정상으로 전달하고, CSRF 헤더만 생력한다.
        // 입력 검증 실패와 CSRF 검증 실패를 구분하기 위합이다.
        mockMvc.perform(post("/api/users")
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "csrf-test@example.com",
                                    "name": "테스트 사용자",
                                    "password": "Password123!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName")
                        .value("AccessDeniedException"))
                .andExpect(jsonPath("$.message")
                        .value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("회원가입 요청에 CSRF 쿠키가 없으면 403을 반환한다")
    void 회원가입_요청에_CSRF_쿠키가_없으면_거절한다() throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        // 발급받은 토큰을 헤더에만 전달한다.
        // 비교할 CSRF 쿠키가 없으므로 요청을 거절해야 한다.
        mockMvc.perform(post("/api/users")
                .header(CSRF_HEADER_NAME, csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "csrf-test@gmail.com",
                            "name" : "테스트 사용자",
                            "password": "Password123!"
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName")
                        .value("AccessDeniedException"))
                .andExpect(jsonPath("$.message")
                        .value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("회원가입 요청의 CSRF 쿠키와 헤더가 다르면 403을 반환한다.")
    void 회원가입_요청의_CSRF_쿠키와_헤더가_다르면_거절한다() throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        // 쿠키는 정상적으로 전달하되, 헤더에는 다른 값을 넣는다.
        String differentToken = csrfCookie.getValue() + "-changed";

        mockMvc.perform(post("/api/users")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, differentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "csrf-test@gmail.com",
                                    "name" : "테스트 사용자",
                                    "password": "Password123!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName")
                        .value("AccessDeniedException"))
                .andExpect(jsonPath("$.message")
                        .value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(userService);
    }


    /**
     * 실제 CSRF 토큰 발급 API를 호출하고 응답 쿠키를 가져온다.
     * <p>
     * 테스트 도구가 만든 토큰이 아닌 서버가 발급한 값을 사용한다.
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
     * CSRF 검증을 통과하면 재발급 서비스가 정상 처리하는 상황을 준비한다.
     * <p>
     * Redis 조회나 JWT 검증 자체는 이번 테스트의 대상이 아니다.
     */
    private void givenReissueSuccess() {
        UserDto userDto = new UserDto(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                LocalDateTime.of(2026, 11, 3, 10, 0),
                "user@gmail.com",
                "테스트 사용자",
                null,
                Role.USER,
                false
        );

        TokenReissueResult result = new TokenReissueResult(
                new JwtDto(userDto, NEW_ACCESS_TOKEN),
                NEW_REFRESH_TOKEN
        );

        given(authService.reissue(OLD_REFRESH_TOKEN))
                .willReturn(result);
    }


}
