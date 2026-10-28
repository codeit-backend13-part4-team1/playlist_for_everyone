package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 인증 필터")
class JwtAuthenticationFilterTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");


    // Provider를 Mock으로 사용하므로 실제 JWT 문자열일 필요는 없다.
    private static final String TOKEN = "test-access-token";
    private static final String EMAIL = "test@gmail.com";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserService userService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;


    @BeforeEach
    void setUp() {
        // 이전 테스트의 인증 정보가 영향을 주지 않도록 초기화한다.
        SecurityContextHolder.clearContext();

        // 이번 테스트에서는 인증 실패시 401을 반환하는 간단한 구현을 사용한다.
        // 실제 JSON 오류 응담은 이후 별도 EntryPoint로 연결한다.
        AuthenticationEntryPoint entryPoint =
                (req, res, excepton) ->
                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED);

        filter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                userService,
                entryPoint
        );

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        // SecurityContextHolder는 스레드에 인증 정보를 보관하므로 정리해야 한다.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 다음 필터로 진행한다")
    void 헤더가_없으면_다음_필터로_진행한다() throws Exception {

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider, userService);
    }

    @Test
    @DisplayName("유효한 토큰이면 사용자 정보와 DB의 권한을 설정한 뒤 진행한다")
    void 유효한_토큰이면_인증_정보를_설정한다() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);

        // 역할은 토큰이 아닌 현재 DB 조회 결과에서 가져온다.
        User user = createUser(Role.ADMIN);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willReturn(USER_ID);

        given(userService.getById(USER_ID))
                .willReturn(user);

        // 다음 필터가 실행되느 시점에 이미 인증 정보가 있어야 한다.
        doAnswer(invocation -> {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            assertThat(authentication).isNotNull();
            assertThat(authentication.isAuthenticated()).isTrue();
            assertThat(authentication.getPrincipal())
                    .isInstanceOf(SecurityUser.class);

            SecurityUser principal =
                    (SecurityUser) authentication.getPrincipal();

            assertThat(principal.getUserId()).isEqualTo(USER_ID);
            assertThat(principal.getUsername()).isEqualTo(EMAIL);
            assertThat(principal.getRole()).isEqualTo(Role.ADMIN);

            assertThat(authentication.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");

            // 토큰 인증 결과에는 비밀번호를 보관하지 않는다.
            assertThat(authentication.getCredentials())
                    .isNull();
            assertThat(principal.getPassword()).isNull();

            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(jwtTokenProvider).parseAccessToken(TOKEN);
        verify(userService).getById(USER_ID);
        verify(filterChain).doFilter(request, response);
    }


    @Test
    @DisplayName("만료된 토큰이면 401을 반환하고 다음 필터를 호출하지 않는다")
    void 만료된_토큰이면_인증에_실패한다() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willThrow(new ExpiredJwtException(
                        null, null, "만료된 토큰"
                ));
        filter.doFilter(request, response, filterChain);

        assertUnauthorized();
        verifyNoInteractions(userService, filterChain);
    }

    @Test
    @DisplayName("잘못된 토큰이면 401을 반환하고 다음 필터를 호출하지 않는다")
    void 잘못된_토큰이면_인증에_실패한다() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willThrow(new JwtException("유효하지 않은 토큰"));

        filter.doFilter(request, response, filterChain);

        assertUnauthorized();
        verifyNoInteractions(userService, filterChain);
    }

    @Test
    @DisplayName("사용자가 잠겨 있으면 유효한 토큰이어도 인증을 거절한다")
    void 잠긴_사용자면_인증에_실패한다() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);

        User user = createUser(Role.USER);
        user.lock();

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willReturn(USER_ID);
        given(userService.getById(USER_ID))
                .willReturn(user);

        filter.doFilter(request, response, filterChain);

        assertUnauthorized();
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("토큰의 사용자가 존재하지 않으면 인증을 거절한다")
    void 사용자가_없으면_인증에_실패한다() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willReturn(USER_ID);
        given(userService.getById(USER_ID))
                .willThrow(new UserNotFoundException(
                        Map.of("userId", USER_ID)
                ));

        filter.doFilter(request, response, filterChain);

        // 일반 사용자 조회의 404 대신, 토큰 인증 실패인 401로 처리한다.
        assertUnauthorized();
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Bearer 뒤에 토큰이 없으면 인증을 거절한다")
    void Bearer_토큰이_비어_있으면_인증에_실패한다() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

        filter.doFilter(request, response, filterChain);

        assertUnauthorized();

        // 빈 값은 필터에서 확인하고 Provider에 넘기지 않는다.
        verifyNoInteractions(jwtTokenProvider, userService, filterChain);
    }

    @Test
    @DisplayName("다음 필터의 일반 오류를 인증 실패로 변환하지 않는다")
    void 다음_필터의_오류는_그대로_전달한다() throws Exception {
        ServletException downstreamException =
                new ServletException("다음 필터에서 발생한 오류");

        doThrow(downstreamException)
                .when(filterChain).doFilter(request, response);

        assertThatThrownBy(
                () -> filter.doFilter(request, response, filterChain)
        ).isSameAs(downstreamException);

        // 일반 서버 오류를 잘못된 인증 응답으로 바꾸면 안 된다.
        assertThat(response.getStatus())
                .isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/sign-in",
            "/api/users"
    })
    @DisplayName("로그인, 회원가입은 잘못된 Bearer 헤더가 있어도 다음 필터로 진행한다.")
    void 공개_인증_요청은_JWT_검증을_생략한다(String path) throws Exception {
        request.setMethod("POST");
        request.setRequestURI(path);

        // 기존 필터에서는 토큰 누락으로 401이 발생하는 헤더다.
        // 공개 인증 요청에서는 헤더 해석하는 단계 자체를 생략해야 한다.
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");
        filter.doFilter(request, response, filterChain);

        // JWT 인증한 것이 아니라, 다음 처리로 넘긴다
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider, userService);
    }

    @Test
    @DisplayName("사용자 목록 조회는 회원가입과 경로가 같아도 JWT 검증을 생략하지 않는다")
    void 사용자_목록_조회는_JWT_검증을_수행한다() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/users");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);

        given(jwtTokenProvider.parseAccessToken(TOKEN))
                .willThrow(new ExpiredJwtException(
                        null, null, "만료된 토큰"
                ));

        filter.doFilter(request, response, filterChain);

        assertUnauthorized();

        // POST /api/users만 제외해야 한다.
        // GET /api/users는 토큰 검증을 수행하고 실패 시 요청을 중단한다.
        verify(jwtTokenProvider).parseAccessToken(TOKEN);
        verifyNoInteractions(userService, filterChain);
    }




    /**
     * DB 없이 사용자 조회 결과를 준비한다.
     */
    private User createUser(Role role) {
        User user = User.builder()
                .email(EMAIL)
                .name("테스트 사용자")
                .password("password")
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        return user;
    }

    /**
     * 인증 실패 시 응답과 인증 정보가 모두 올바른지 확인한다.
     */
    private void assertUnauthorized() {
        assertThat(response.getStatus())
                .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }







}