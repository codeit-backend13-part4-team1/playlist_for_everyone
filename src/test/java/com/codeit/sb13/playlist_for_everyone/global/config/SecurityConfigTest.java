package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.controller.UserController;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        SecurityConfigTest.SecurityTestController.class
})
@DisplayName("SecurityFilterChain 연결")
class SecurityConfigTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String TOKEN = "test-access-token";
    private static final String ME_URL = "/api/security-test/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager  authenticationManager;


    @Test
    @DisplayName("회원가입은 로그인하지 않아도 접근할 수 있다.")
    void 비로그인_회원가입을_허용한다() throws Exception {
        UserDto response = new UserDto(
                USER_ID,
                LocalDateTime.of(2026, 10, 27, 0, 0),
                "test@gmail.com",
                "테스트 사용자",
                null,
                Role.USER,
                false
        );

        given(userService.create(any(UserCreateCommand.class)))
                .willReturn(response);

        // 인증 정보는 제공하지 않는다.
        // CSRF 토큰은 제공해서 비로그인 접근 허용 정책만 검증한다.
        mockMvc.perform(post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "test@gmail.com",
                          "name" : "테스트 사용자",
                          "password" : "test-password"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("보호된 API에 토큰 없이 접근하면 401을 반환한다.")
    void 보호된_API는_인증이_필요하다() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding(StandardCharsets.UTF_8.name()))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(jwtTokenProvider, userService);
    }

    @Test
    @DisplayName("유효한 JWT로 접근하면 Controller에서 인증 사용자를 받는다.")
    void JWT_인증_정보를_Controller에_전달한다() throws Exception {
        prepareAuthenticatedUser(Role.USER);

        mockMvc.perform(get(ME_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("유효하지 않은 JWT로 접근하면 401을 반환한다")
    void 잘못된_JWT는_거절한다() throws Exception {
        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willThrow(new JwtException("내부 토큰 검증 오류"));

        mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName")
                        .value("UnauthorizedException"))
                // 내부 예외 메시지를 그대로 노출하지 않는다.
                .andExpect(jsonPath("$.message")
                        .value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("일반 사용자는 관리자 사용자 목록 API에 접근할 수 없다.")
    void 일반_사용자의_관리자_API_접근을_거절한다() throws Exception {
        prepareAuthenticatedUser(Role.USER);

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().encoding(StandardCharsets.UTF_8.name()))
                .andExpect(jsonPath("$.exceptionName")
                        .value("AccessDeniedException"))
                .andExpect(jsonPath("$.message")
                        .value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("관리자는 관리자 사용자 목록 API에 접근할 수 있다")
    void 관리자의_관리자_API_접근을_허용한다() throws Exception {
        prepareAuthenticatedUser(Role.ADMIN);

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @DisplayName("인증 정보를 세션이나 다음 요청에 남기지 않는다.")
    void 요청_사이에_인증_정보가_남지_않는다() throws Exception {
        prepareAuthenticatedUser(Role.USER);

        // 첫 번째 요청은 JWT로 인증한다.
        MvcResult firstResult = mockMvc.perform(get(ME_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andReturn();

        // JWT 인증을 위해 HttpSession을 생성하지 않아야 한다.
        assertThat(firstResult.getRequest().getSession(false))
                .isNull();

        // 실제 Security 필터 체인은 요청 종료 후 Context를 정리해야 한다.
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        // 이전 요청이 인증되었어도, 토큰 없는 다음 요청은 거절해야 한다.
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }


    /**
     * 실제 JWT 암호 연산과 DB 조회를 대신해 인증에 필요한 결과를 준비한다.
     *
     * JwtAuthenticationFilter와 SecurityFilterChain은 실제 객체를 사용한다.
     */
    private void prepareAuthenticatedUser(Role role) {
        User user = User.builder()
                .email("test@gmail.com")
                .name("테스트 사용자")
                .password("test-password")
                .role(role)
                .build();

        ReflectionTestUtils.setField(user, "id", USER_ID);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willReturn(USER_ID);

        given(userService.getById(USER_ID))
                .willReturn(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config"
    })
    @DisplayName("dev가 아닌 환경에서 문서 경로를 비로그인 사용자에게 공개하지 않는다.")
    void 개발_환경_외에서는_문서_접근에_인증이_필요하다(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtTokenProvider, userService);
    }


    /**
     *  보안 설정을 검증하기 위한 테스트 전용 Controller.
     *
     *  실제 운영 API로 추가되는 클래스가 아니다.
     */
    @RestController
    public static class SecurityTestController {

        @GetMapping("/api/security-test/me")
        public Map<String, String> me(@AuthenticationPrincipal SecurityUser principal) {
            // RED 단계에서 인증 없이 Controller에 도달한 경우를 구분한다.
            // null 때문에 500이 발생하지 않도록 테스트용 응답을 반환한다.
            if (principal == null) {
                return Map.of("userId", "anonymous");
            }

            return Map.of(
                    "userId", principal.getUserId().toString(),
                    "role", principal.getRole().name()
            );
        }

        @GetMapping("/api/users")
        public Map<String, Boolean> users() {
            // 실제 관리자 목록 구현 대신 URL 접근 권한만 검증한다.
            return Map.of("allowed", true);
        }

    }





}