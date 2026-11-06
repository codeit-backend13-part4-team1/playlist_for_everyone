package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentAuthorizationTest.TestController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        ContentAuthorizationTest.TestController.class
})
@ActiveProfiles("test")
@DisplayName("콘텐츠 생성, 수정, 삭제 권한 테스트")
public class ContentAuthorizationTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String TOKEN = "test-access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuthService authService;

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

    @RestController
    static class TestController {

        @PostMapping("/api/contents")
        @ResponseStatus(HttpStatus.CREATED)
        public void create() {
        }

        @PatchMapping("/api/contents/{contentId}")
        public void update() {
        }

        @DeleteMapping("/api/contents/{contentId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete() {
        }
    }

    @Test
    @DisplayName("일반 사용자는 콘텐츠를 생성할 수 없다.")
    void 일반_사용자의_콘텐츠_생성을_거절한다() throws Exception {
        // given
        prepareAuthenticatedUser(Role.USER);

        // when & then
        mockMvc.perform(post("/api/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 콘텐츠를 생성할 수 있다.")
    void 관리자의_콘텐츠_생성을_허용한다() throws Exception {
        // given
        prepareAuthenticatedUser(Role.ADMIN);

        // when & then
        mockMvc.perform(post("/api/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("일반 사용자는 콘텐츠를 수정할 수 없다.")
    void 일반_사용자의_콘텐츠_수정을_거절한다() throws Exception {
        // given
        prepareAuthenticatedUser(Role.USER);
        UUID contentId = UUID.randomUUID();

        // when & then
        mockMvc.perform(patch("/api/contents/{contentId}", contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 콘텐츠를 수정할 수 있다.")
    void 관리자의_콘텐츠_수정을_허용한다() throws Exception {
        // given
        prepareAuthenticatedUser(Role.ADMIN);
        UUID contentId = UUID.randomUUID();

        // when & then
        mockMvc.perform(patch("/api/contents/{contentId}", contentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                    .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일반 사용자는 콘텐츠를 삭제할 수 없다.")
    void 일반_사용자의_콘텐츠_삭제를_거절한다() throws Exception {
        // given
        prepareAuthenticatedUser(Role.USER);
        UUID contentId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/contents/{contentId}", contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 콘텐츠를 삭제할 수 있다.")
    void 관리자의_콘텐츠_삭제를_허용한다() throws Exception {
        // given
        prepareAuthenticatedUser(Role.ADMIN);
        UUID contentId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/contents/{contentId}", contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

}
