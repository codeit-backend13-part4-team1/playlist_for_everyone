package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 성공 처리")
class JwtAuthenticationSuccessHandlerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String EMAIL = "test@gmail.com";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ENCODED_PASSWORD = "encoded_password";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

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
}