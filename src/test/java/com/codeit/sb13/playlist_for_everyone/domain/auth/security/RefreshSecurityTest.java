package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.controller.AuthController;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.authentication.AuthenticationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                // 고정된 7일이 아닌 설정값인 3일이 사용되는지 확인한다.
                "everyone.jwt.refresh-token-validity=3d",
                "everyone.jwt.refresh-cookie-secure=true"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("토큰 재발급 API 보안 연결")
class RefreshSecurityTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");


    // 실제 JWT 대신 사용하는 테스트 문자열이다.
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "now-access-token";
    private static final String EXPIRED_ACCESS_TOKEN = "expired-access-token";

    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(3);

    @Autowired
    private MockMvc mockMvc;

    // 서비스 내부 로직은 AuthServiceTest에서 검증
    // 여기서는 컨트롤러 보안 필터, HTTP 응답의 연결을 확인한다.
    @MockitoBean
    private AuthService authService;

    // SecurityConfig가 필요로 하는 의존성
    // Redis 연결을 수행하지 않음
    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    // SecurityConfig가 로그인 세션 저장소를 주입받는다.
    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @Test
    @DisplayName("Access Token 없이 Refresh Token 쿠키로 재발급 한다.")
    void 쿠키로_재발급에_성공한다() throws Exception {
        givenReissueSuccess();


        MvcResult result = mockMvc.perform(
                        refreshRequest()
                                .cookie(refreshCookie(OLD_REFRESH_TOKEN))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value(NEW_ACCESS_TOKEN))
                .andExpect(jsonPath("$.userDto.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.email").value("user@gmail.com"))
                .andExpect(jsonPath("$.userDto.name").value("테스트 사용자"))
                .andExpect(jsonPath("$.userDto.role").value("USER"))
                .andExpect(jsonPath("$.userDto.locked").value(false))
                // 내부 결과 객체나 Refresh Token을 JSON에 노출하지 않는다.
                .andExpect(jsonPath("$.jwtDto").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.userDto.password").doesNotExist())
                .andReturn();

        // 쿠키에서 읽은 기존 토큰을 서비스에 전달해야 한다.
        verify(authService).reissue(OLD_REFRESH_TOKEN);

        // 다른 쿠키가 추가되더라고 Refresh Token 쿠키만 선택한다.
        var refreshCookie = result.getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(value -> value.startsWith("REFRESH_TOKEN="))
                .toList();

        assertThat(refreshCookie).hasSize(1);

        assertThat(refreshCookie.get(0))
                .startsWith("REFRESH_TOKEN=" + NEW_REFRESH_TOKEN + ";")
                .contains("HttpOnly")
                .contains("; Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/auth")
                .contains("Max-Age=" + REFRESH_TOKEN_VALIDITY.toSeconds())
                .doesNotContain("Domain=");

        // Refresh Token 원문이 응답 JSON에 포함되면 안 된다.
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(OLD_REFRESH_TOKEN, NEW_REFRESH_TOKEN);

        // 세션과 리다이렉트를 만들지 않는다.
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("만료된 Access Token이 함께 있어소 Refresh Token으로 재발급 한다.")
    void 만료된_Access_Token이_재발급을_막지_않는다() throws Exception {
        givenReissueSuccess();

        // 필터가 Access Token을 검사하면 만료 예외가 발생하는 상황이다.
        given(jwtTokenProvider.parseAccess(EXPIRED_ACCESS_TOKEN))
                .willThrow(new ExpiredJwtException(
                        null,
                        null,
                        "Access Token이 만료되었습니다."
                ));

        mockMvc.perform(refreshRequest()
                                .cookie(refreshCookie(OLD_REFRESH_TOKEN))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(NEW_ACCESS_TOKEN));

        verify(authService).reissue(OLD_REFRESH_TOKEN);

        // 재발급 요청에서는 Access Token 검증을 건너뛰어야 한다.
        verify(jwtTokenProvider, never()).parseAccess(anyString());
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 서비스 호출 없이 401을 반환한다.")
    void 쿠키가_없으면_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(refreshRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("BadCredentialsException"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."))
                .andReturn();

        // 입력 단계에서 거절하므로 서비스를 호출하지 않는다.
        verifyNoInteractions(authService);
        assertNoRefreshCookie(result);
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 비어 있으면 서비스 호출 없이 401을 반환한다")
    void 빈_쿠키를_거절한다() throws Exception {
        MvcResult result = mockMvc.perform(
                        refreshRequest().cookie(refreshCookie(""))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exceptionName")
                        .value("BadCredentialsException"))
                .andExpect(jsonPath("$.message")
                        .value("유효하지 않은 인증 정보입니다."))
                .andReturn();

        verifyNoInteractions(authService);
        assertNoRefreshCookie(result);
    }

    @Test
    @DisplayName("서비스의 인증 실패를 401 JSON으로 반환한다")
    void 인증_실패를_401로_반환한다() throws Exception {
        given(authService.reissue(OLD_REFRESH_TOKEN))
                .willThrow(new BadCredentialsException(
                        "유효하지 않은 인증 정보입니다."
                ));

        MvcResult result = mockMvc.perform(
                        refreshRequest()
                                .cookie(refreshCookie(OLD_REFRESH_TOKEN))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exceptionName")
                        .value("BadCredentialsException"))
                .andExpect(jsonPath("$.message")
                        .value("유효하지 않은 인증 정보입니다."))
                .andReturn();

        // 앞단 필터의 401을 서비스 인증 실패 처리로 착각하지 않도록,
        // 실제 서비스 호출 여부까지 확인한다.
        verify(authService).reissue(OLD_REFRESH_TOKEN);
        assertNoRefreshCookie(result);
    }

    @Test
    @DisplayName("저장소 장애는 인증 실패가 아닌 500으로 반환한다")
    void 서버_장애를_500으로_유지한다() throws Exception {
        given(authService.reissue(OLD_REFRESH_TOKEN))
                .willThrow(new DataAccessResourceFailureException(
                        "Redis 연결 실패"
                ));

        MvcResult result = mockMvc.perform(
                        refreshRequest()
                                .cookie(refreshCookie(OLD_REFRESH_TOKEN))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andReturn();

        verify(authService).reissue(OLD_REFRESH_TOKEN);
        assertNoRefreshCookie(result);
    }


    /**
     * 서비스 재발급에 성공한 상황을 준비한다.
     * 내부 반환값에는 응답용 JwtDto와 쿠키용 Refresh Token이 들어 있다.
     */
    private void givenReissueSuccess() {
        UserDto userDto = new UserDto(
                USER_ID,
                LocalDateTime.of(2026, 10, 31, 10, 0),
                "user@gmail.com",
                "테스트 사용자",
                null,
                Role.USER,
                false
        );


        given(authService.reissue(OLD_REFRESH_TOKEN))
                .willReturn(new TokenReissueResult(
                        new JwtDto(userDto, NEW_ACCESS_TOKEN),
                        NEW_REFRESH_TOKEN
                ));
    }

    private MockHttpServletRequestBuilder refreshRequest() {
        // 요청 본문이나 로그인 Principal 없이 쿠키로 검증한다.
        // CSRF 정책 자체는 별도 작업에서 검증한다.
        return post("/api/auth/refresh").with(csrf());
    }

    private Cookie refreshCookie(String token) {
        return new Cookie("REFRESH_TOKEN", token);
    }

    private void assertNoRefreshCookie(MvcResult result) {
        // 실패한 요청에서는 새 Refresh Token 쿠키를 발급하지 않는다.
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("REFRESH_TOKEN="));
    }
}
