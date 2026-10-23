package com.codeit.sb13.playlist_for_everyone.domain.user.controller;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        MDCLoggingInterceptor.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("정상 요청이면 201과 사용자 정보를 반환한다")
    void 정상_요청이면_사용자를_생성한다() throws Exception {

        UUID userId = UUID.randomUUID();

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password"
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
                "password"
        );

        given(userService.create(command))
                .willThrow(new DuplicateEmailException(command.email()));

        mockMvc.perform(post("/api/users")
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



}