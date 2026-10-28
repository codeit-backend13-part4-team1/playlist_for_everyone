package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.controller.UserController;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.PasswordEncoderConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityAuthenticationConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
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

    private static final String EMAIL = "test@gmail.com";
    private static final String PASSWORD = "test=password";
    private static final String ACCESS_TOKEN = "issued-access-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoderConfig passwordEncoderConfig;

    // 사용자 조회 결과만 대신한다.
    // AuthenticationManager와 비밀번호 검증 Provider는 실제 객체다.
    @MockitoBean
    private CustomUserDetailsService userDetailsService;


    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인하면 토큰과 사용자 정보를 반환한다")
    void 정상_로그인에_성공한다() throws Exception {
        prepareSecurityUser(false);

        User user = User.builder()
                .email(EMAIL)
                .name("테스트 사용자")
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 10, 28, 9, 0));

        given(userService.getById(USER_ID)).willReturn(user);
        given(jwtTokenProvider.createAccessToken(USER_ID)).willReturn(ACCESS_TOKEN);

        MvcResult result = mockMvc.perform(signIn(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.userDto.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.email").value(EMAIL))
                .andExpect(jsonPath("$.userDto.name").value("테스트 사용자"))
                .andExpect(jsonPath("$.userDto.password").doesNotExist())
                .andReturn();

        // 성공 응답뿐 아니라 실제 인증 경로가 실행됐는지도 확인한다.
        verify(userDetailsService).loadUserByUsername(EMAIL);
        verify(jwtTokenProvider).createAccessToken(USER_ID);

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

    private MockHttpServletRequestBuilder signIn(String password) {
        // 명세에 맞춰 JSON이 아닌 폼 파라미터로 요청한다.
        // username에는 사용자의 이메일이 들어간다.
        return post("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", password)

                // 이번 테스트는 로그인 처리에 집중한다.
                // CSRF 검증 정책은 이후에 별도로 진행한다.
                .with(csrf());
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
        verifyNoInteractions(userService, jwtTokenProvider);

        assertThat(result.getResponse().getRedirectedUrl()).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

    }











}
