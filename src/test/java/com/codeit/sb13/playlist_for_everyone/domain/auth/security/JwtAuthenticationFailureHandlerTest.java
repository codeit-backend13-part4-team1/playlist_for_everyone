package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("로그인 실패 처리")
class JwtAuthenticationFailureHandlerTest {

    private static final String INTERNAL_MESSAGE = "외부에 노출하면 안 되는 내부 인증 정보";

    static Stream<AuthenticationException> authenticationExceptions() {
        return Stream.of(
                new BadCredentialsException(INTERNAL_MESSAGE),
                new UsernameNotFoundException(INTERNAL_MESSAGE),
                new LockedException(INTERNAL_MESSAGE)
        );
    }

    @ParameterizedTest
    @MethodSource("authenticationExceptions")
    @DisplayName("인증 실패 종류와 관계없이 동일한 401 JSON 응답을 반환한다")
    void 인증_실패를_공통_응답으로_반환한다(AuthenticationException exception) throws Exception {

        JsonMapper objectMapper = JsonMapper.builder().build();

        JwtAuthenticationFailureHandler handler = new JwtAuthenticationFailureHandler(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isNotNull();
        assertThat(MediaType.parseMediaType(response.getContentType())
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(response.getCharacterEncoding())
                .isEqualToIgnoringCase(StandardCharsets.UTF_8.name());

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(content);

        // 원래 예외의 클래스명과 메시지 대신 외부 응답을 통일한다.
        assertThat(body.path("exceptionName").asString())
                .isEqualTo("BadCredentialsException");
        assertThat(body.path("message").asString())
                .isEqualTo("로그인에 실패했습니다.");

        assertThat(body.path("details").isObject()).isTrue();
        assertThat(body.path("details").size()).isZero();

        assertThat(content).doesNotContain(INTERNAL_MESSAGE);

        // REST API이므로 실패 페이지 이동이나 세션 생성이 없어야 한다.
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession(false)).isNull();
    }
}