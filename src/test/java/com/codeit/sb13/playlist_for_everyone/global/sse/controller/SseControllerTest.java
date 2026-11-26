package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.AccessTokenClaims;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC 요청 바인딩과 보안 필터, SSE 응답 시작·종료를 확인하는 컨트롤러 테스트다.
 * 연결 서비스와 인증의 외부 의존은 Mock으로 대체하며, 실제 네트워크 소켓은 열지 않는다.
 *
 * <p>일반 API 계약은 user(principal)로 인증을 준비한다. 비동기 종료 회귀 테스트에서는
 * Bearer 요청으로 실제 JWT 필터를 거쳐 요청 속성에 인증이 저장되는 경로까지 확인한다.
 */
@WebMvcTest(SseController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class
})
@DisplayName("SSE 연결 컨트롤러")
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseConnectionService sseConnectionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    // SecurityConfig가 로그아웃 핸들러를 생성할 때 필요한 의존성이다.
    @MockitoBean
    private AuthService authService;

    // SecurityConfig가 로그인 세션 저장소를 주입받는다.
    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @Test
    @DisplayName("최초 연결은 인증된 사용자 ID와 세션 ID를 전달하고 마지막 이벤트 ID는 null로 전달한다.")
    void connectPassesNullLastEventIdWhenOmitted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID loginSessionId = UUID.randomUUID();
        LoginSession loginSession = new LoginSession(loginSessionId);
        SecurityUser principal = getPrincipal(userId, "sse-test@example.com", loginSessionId);

        SseEmitter emitter = new SseEmitter(30_000L);

        // MVC 초기화 전 send는 버퍼에 보관된다. 컨트롤러가 반환한 뒤 최초 주석 응답이 시작된다.
        emitter.send(SseEmitter.event().comment("connected"));

        given(sseConnectionService.connect(userId, null, loginSession)).willReturn(emitter);

        try {

            mockMvc.perform(get("/api/sse")
                        .with(user(principal))
                        .accept(MediaType.TEXT_EVENT_STREAM)
                    )
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            ;

            verify(sseConnectionService).connect(userId, null, loginSession);
        }finally {

            emitter.complete();
        }
    }

    @Test
    @DisplayName("미인증 사용자가 SSE 연결을 요청하면 401을 반환하고 연결 서비스를 호출하지 않는다.")
    void connectRejectsUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
        ).andExpect(status().isUnauthorized())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(sseConnectionService);
    }

    @Test
    @DisplayName("잘못된 lastEventId로 SSE 연결을 요청하면 400과 공통 JSON 오류 응답을 반환한다.")
    void connectRejectsInvalidLastEventId() throws Exception {
        UUID loginSessionId = UUID.randomUUID();
        SecurityUser principal = getPrincipal(UUID.randomUUID(), "see-test@example.com", loginSessionId);
        mockMvc.perform(get("/api/sse")
                        .with(user(principal))
                        .param("lastEventId","not-a-uuid")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                )
                .andExpect(status().isBadRequest())
                .andExpect(request().asyncNotStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentTypeMismatchException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(sseConnectionService);


    }

    @Test
    @DisplayName("재연결은 인증된 사용자 ID와 세션 ID, 요청의 마지막 이벤트 ID를 전달한다.")
    void connectPassesLastEventIdToService() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        UUID loginSessionId = UUID.randomUUID();
        LoginSession loginSession = new LoginSession(loginSessionId);

        SecurityUser principal = getPrincipal(userId, "sse-test@example.com", loginSessionId);

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));

        given(sseConnectionService.connect(userId, lastEventId, loginSession)).willReturn(emitter);

        try {
            mockMvc.perform(get("/api/sse")
                    .with(user(principal))
                    .param("lastEventId", lastEventId.toString())
                    .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

            verify(sseConnectionService).connect(userId, lastEventId, loginSession);
        } finally {
            emitter.complete();
        }
    }

    @Test
    @DisplayName("요청 파라미터에 다른 사용자 ID가 있어도 인증된 사용자로만 SSE 연결을 새성한다.")
    void connectUsesAuthenticatedUserInsteadOfQueryUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID loginSessionId = UUID.randomUUID();
        LoginSession loginSession = new LoginSession(loginSessionId);

        SecurityUser principal = getPrincipal(userId, "sse-test@example.com", loginSessionId);

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));

        given(sseConnectionService.connect(userId, null, loginSession)).willReturn(emitter);

        try{
            mockMvc.perform(get("/api/sse")
                    .with(user(principal))
                    .param("userId", otherUserId.toString())
                    .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

            verify(sseConnectionService).connect(userId, null, loginSession);
            verify(sseConnectionService, never()).connect(otherUserId, null, loginSession);
        } finally {
            emitter.complete();
        }
    }

    @Test
    @DisplayName("인증된 SSE 연결을 종료하면 비동기 재처리도 보안 예외 없이 완료된다.")
    void completeStreamAllowsAsyncDispatchForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "sse-test-token";
        UUID loginSessionId = UUID.randomUUID();
        LoginSession loginSession = new LoginSession(loginSessionId);

        User user = mock(User.class);

        given(user.getId()).willReturn(userId);
        given(user.getEmail()).willReturn("sse-test@example.com");
        given(user.getRole()).willReturn(Role.USER);
        given(user.isLocked()).willReturn(false);

        given(jwtTokenProvider.parseAccess(token))
                .willReturn(new AccessTokenClaims(userId, loginSessionId));
        given(userSessionRepository.isCurrent(userId, loginSessionId)).willReturn(true);
        given(userService.getById(userId)).willReturn(user);

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));
        given(sseConnectionService.connect(userId, null, loginSession)).willReturn(emitter);

        try {
            MvcResult result = mockMvc.perform(get("/api/sse")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();
            // complete로 비동기 결과를 만든 뒤 MockMvc에서 후속 ASYNC 디스패치를 명시적으로 실행한다.
            // 이때 새 토큰 검증 없이 같은 요청의 저장된 인증을 복원해야 정상 종료할 수 있다.
            emitter.complete();
            mockMvc.perform(asyncDispatch(result))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncNotStarted());
            verify(sseConnectionService).connect(userId, null, loginSession);
        } finally {
            emitter.complete();
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void disconnectDoesNotLogServerErrorOrWriteErrorBody(CapturedOutput output) throws Exception {
        List<ILoggingEvent> events = captureWriteFailureLogs(new IOException("Broken pipe"));

        assertThat(events)
                .filteredOn(ILoggingEvent::getFormattedMessage, "SSE 클라이언트 연결이 종료되었습니다.")
                .extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.DEBUG);

        assertThat(output.getAll()).doesNotContain(
                "처리되지 않은 서버 오류가 발생했습니다.",
                "Failure in @ExceptionHandler");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unexpectedWriteFailureIsLoggedWithoutWritingErrorBody(CapturedOutput output) throws Exception {
        IOException writeFailure = new IOException("simulated write failure");
        List<ILoggingEvent> events = captureWriteFailureLogs(writeFailure);

        assertThat(events)
                .filteredOn(ILoggingEvent::getFormattedMessage, "SSE 응답 쓰기 중 예상하지 못한 오류가 발생했습니다.")
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getThrowableProxy()).isInstanceOfSatisfying(
                            ThrowableProxy.class,
                            proxy -> assertThat(proxy.getThrowable().getCause()).isSameAs(writeFailure));
                });

        assertThat(output.getAll()).doesNotContain(
                "처리되지 않은 서버 오류가 발생했습니다.",
                "Failure in @ExceptionHandler");
    }

    private List<ILoggingEvent> captureWriteFailureLogs(IOException cause) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SseController.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            logger.setLevel(Level.DEBUG);
            dispatchStreamWriteFailure(cause);
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private void dispatchStreamWriteFailure(IOException cause) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID loginSessionId = UUID.randomUUID();
        String token = "sse-test-token";

        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getEmail()).willReturn("sse-test@example.com");
        given(user.getRole()).willReturn(Role.USER);
        given(user.isLocked()).willReturn(false);
        given(jwtTokenProvider.parseAccess(token))
                .willReturn(new AccessTokenClaims(userId, loginSessionId));
        given(userSessionRepository.isCurrent(userId, loginSessionId)).willReturn(true);
        given(userService.getById(userId)).willReturn(user);

        SseEmitter emitter = new SseEmitter(30_000L);
        emitter.send(SseEmitter.event().comment("connected"));
        given(sseConnectionService.connect(userId, null, new LoginSession(loginSessionId)))
                .willReturn(emitter);

        try {
            MvcResult result = mockMvc.perform(get("/api/sse")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(content().string(containsString(":connected")))
                    .andReturn();

            assertThat(result.getResponse().isCommitted()).isTrue();
            byte[] streamBody = result.getResponse().getContentAsByteArray();

            emitter.completeWithError(new AsyncRequestNotUsableException(
                    "Servlet response write failed", cause));

            mockMvc.perform(asyncDispatch(result))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(streamBody));
        } finally {
            emitter.complete();
        }
    }

    private SecurityUser getPrincipal(UUID userId, String mail, UUID loginSessionId) {
        return new SecurityUser(userId, mail, "", Role.USER, false, loginSessionId);
    }
}
