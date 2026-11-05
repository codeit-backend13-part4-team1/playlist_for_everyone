package com.codeit.sb13.playlist_for_everyone.domain.user.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserAdministrationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpHeaders;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        MDCLoggingInterceptor.class
})
@ActiveProfiles("test")
class UserControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TARGET_USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String TOKEN = "test-access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("정상 요청이면 201과 사용자 정보를 반환한다")
    void 정상_요청이면_사용자를_생성한다() throws Exception {

        UUID userId = UUID.randomUUID();

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password",
                Role.USER
        );

        UserDto response = new UserDto(
                userId,
                LocalDateTime.of(2026, 10, 23, 10, 0),
                "test@gmail.com",
                "테스트",
                null,
                Role.USER,
                false
        );

        given(userService.create(command)).willReturn(response);

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                 "email": "test@gmail.com",
                                 "name": "테스트",
                                 "password": "password" 
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.name").value("테스트"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).create(command);
    }

    @ParameterizedTest(name = "{index}: 잘못된 회원가입 요청")
    @MethodSource("invalidRequest")
    @DisplayName("입력 검증에 실패하면 400을 반환하고 서비스를 호출하지 않는다")
    void 잘못된_입력이면_가입하지_않는다(String requestBody) throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.details").isMap());

        verifyNoInteractions(userService);

    }

    static Stream<String> invalidRequest() {
        return Stream.of(
                   """
                   {
                    "email" : "invalid-email",
                    "name" : "테스트",
                    "password": "password"
                   }
                   """,
                   """
                   {
                     "email" : "test@gmail.com",
                     "name" : "    ",
                     "password": "password"
                   }
                   """,
                   """
                   {
                     "email" : "test@gmail.com",
                     "name" : "테스트",
                     "password": ""
                   }
                   """
        );
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 400과 중복 오류를 반환한다")
    void 중복_이메일이면_오류를_반환한다() throws Exception {
        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password",
                Role.USER
        );

        given(userService.create(command))
                .willThrow(new DuplicateEmailException(command.email()));

        mockMvc.perform(post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                         "email": "test@gmail.com",
                         "name": "테스트",
                         "password" : "password" 
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("DuplicateEmailException"))
                .andExpect(jsonPath("$.details.email")
                        .value("test@gmail.com"));

        verify(userService).create(command);
    }

    @ParameterizedTest(name = "{index}: 72바이트 초과 비밀번호")
    @MethodSource("tooLongPasswords")
    @DisplayName("비밀번호가 72바이트를 초과하면 400을 반환한다")
    void 비밀번호가_72바이트를_초과하면_거절한다(String password) throws Exception {
        String requestBody = """
                {
                "email": "test@gmail.com",
                "name": "테스트",
                "password": "%s"
                }
                """.formatted(password);

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.details.password").isArray());

        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("관리자가 권한을 변경하면 서비스를 호출하고 204를 반환한다")
    void 관리자는_권한을_변경한다(Role newRole) throws Exception {
        prepareAuthenticatedUser(Role.ADMIN);

        mockMvc.perform(patch("/api/users/{userId}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "%s"}
                                """.formatted(newRole.name())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userAdministrationService)
                .updateRole(TARGET_USER_ID, newRole);
        verifyNoMoreInteractions(userAdministrationService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("관리자가 잠금 상태를 변경하면 서비스를 호출하고 204를 반환한다")
    void 관리자는_잠금_상태를_변경한다(boolean locked) throws Exception {
        prepareAuthenticatedUser(Role.ADMIN);

        mockMvc.perform(patch("/api/users/{userId}/locked", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locked": %s}
                                """.formatted(locked)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userAdministrationService)
                .updateLocked(TARGET_USER_ID, locked);
        verifyNoMoreInteractions(userAdministrationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"role\": null}",
            "{\"role\": \"MANAGER\"}",
            "{\"role\": \"\"}"
    })
    @DisplayName("권한이 누락되거나 잘못되면 400을 반환한다")
    void 잘못된_권한_요청을_거절한다(String body) throws Exception {
        prepareAuthenticatedUser(Role.ADMIN);

        mockMvc.perform(patch("/api/users/{userId}/role", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAdministrationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"locked\": null}"
    })
    @DisplayName("잠금 상태가 누락되면 400을 반환한다")
    void 잠금_상태_누락을_거절한다(String body) throws Exception {
        prepareAuthenticatedUser(Role.ADMIN);

        mockMvc.perform(patch("/api/users/{userId}/locked", TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAdministrationService);
    }

    @ParameterizedTest
    @CsvSource({
            "role, ADMIN",
            "locked, true"
    })
    @DisplayName("일반 사용자는 권한과 잠금 상태를 변경할 수 없다")
    void 일반_사용자의_변경을_거절한다(
            String field,
            String value
    ) throws Exception {
        prepareAuthenticatedUser(Role.USER);

        mockMvc.perform(patch("/api/users/{userId}/" + field, TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(field, value)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdministrationService);
    }

    @ParameterizedTest
    @CsvSource({
            "role, ADMIN",
            "locked, true"
    })
    @DisplayName("인증되지 않은 사용자는 권한과 잠금 상태를 변경할 수 없다")
    void 비인증_사용자의_변경을_거절한다(
            String field,
            String value
    ) throws Exception {
        // CSRF는 제공하여 인증 실패와 CSRF 실패를 구분한다.
        mockMvc.perform(patch("/api/users/{userId}/" + field, TARGET_USER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(field, value)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAdministrationService);
    }

    @ParameterizedTest
    @CsvSource({
            "role, ADMIN",
            "locked, true"
    })
    @DisplayName("관리자도 CSRF 토큰 없이 상태를 변경할 수 없다")
    void CSRF_없는_변경을_거절한다(
            String field,
            String value
    ) throws Exception {
        prepareAuthenticatedUser(Role.ADMIN);

        mockMvc.perform(patch("/api/users/{userId}/" + field, TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(field, value)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAdministrationService);
    }

    static Stream<String> tooLongPasswords() {
        return Stream.of(
                "a".repeat(73),
                "가".repeat(25)
        );
    }

    private void prepareAuthenticatedUser(Role role) {
        User requester = User.builder()
                .email("requester@example.com")
                .name("요청 사용자")
                .password("encoded-password")
                .role(role)
                .build();
        ReflectionTestUtils.setField(requester, "id", ADMIN_ID);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willReturn(ADMIN_ID);
        given(userService.getById(ADMIN_ID))
                .willReturn(requester);
    }

    private String requestBody(String field, String value) {
        if ("role".equals(field)) {
            return """
                    {"role": "%s"}
                    """.formatted(value);
        }

        return """
                {"locked": %s}
                """.formatted(value);
    }
}