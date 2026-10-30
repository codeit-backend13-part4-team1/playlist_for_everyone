package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SseConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

/**
 * 실제 내장 Tomcat에 HTTP 요청을 보내 클라이언트가 받는 SSE 프레임을 검증한다.
 * JSON 변환 실패 후 기존 연결을 유지하고 같은 HTTP 스트림에서 다음 정상 이벤트를 수신하는지도 확인한다.
 * MVC·보안 필터·연결/전송 서비스·메모리 저장소는 실제로 동작하고, JWT 검증·사용자 조회는 대체한다.
 * DB, Redis, 주기적 하트비트는 이 컨텍스트에 포함하지 않아 프레임 전송 계약에 집중한다.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = {
                SseController.class,
                SseConnectionService.class,
                SseDeliveryService.class,
                InMemorySseEmitterRepository.class,
                SseConfig.class,
                SecurityConfig.class
        },
        properties = {
                "everyone.sse.connection-timeout=30s"
        }
)
// 전체 애플리케이션 대신 명시한 구성만 띄우므로 웹 서버와 직렬화, 보안 필터 등록도 직접 가져온다.
// SecurityConfig의 필터 체인이 실제 HTTP 요청에 적용되도록 SecurityFilterAutoConfiguration을 포함한다.
@ImportAutoConfiguration({
        TomcatServletWebServerAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
// 테스트 클래스가 끝나면 이 컨텍스트와 내장 서버를 정리한다. 각 요청의 연결은 finally에서 먼저 닫는다.
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@DisplayName("SSE 실제 HTTP 스트리밍 통합 테스트")
public class SseStreamingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SseConnectionService sseConnectionService;

    @Autowired
    private SseDeliveryService sseDeliveryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SseEmitterRepository sseEmitterRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;


    @Test
    @DisplayName("인증된 HTTP 요청으로 SSE에 연결하면 최초 연결 주석 프레임을 받는다.")
    void connectReceivesInitialCommentOverHttp() throws IOException {
        UUID userId = UUID.randomUUID();
        String token = "sse-test-token";

        prepareAuthenticatedUser(userId, token);

        HttpURLConnection connection = createSseConnection(token);

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            MediaType contentType = MediaType.parseMediaType(connection.getContentType());
            assertThat(contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                // SSE는 연결이 계속 열려 있으므로 EOF까지 읽지 않고 필요한 줄만 읽는다.
                // ':'는 주석을 뜻하며 뒤의 빈 줄은 프레임의 끝이다. 최초 주석에는 id가 없다.
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEqualTo("");
            }
        } finally {
            try {
                // 서버의 종료 처리도 실행하여 비동기 응답을 정리하고, 실패하더라도 클라이언트 연결은 해제한다.
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connection.disconnect();
            }
        }
    }

    @ParameterizedTest(name = "{0} 이벤트는 {1} 이름으로 전달된다.")
    // 기대 이름은 enum에서 다시 계산하지 않고 명세 문자열로 적어 이름 매핑 오류도 검출한다.
    @CsvSource({
            "NOTIFICATIONS, notifications",
            "DIRECT_MESSAGES, direct-messages"
    })
    @DisplayName("알림 과 DM 이벤트의 ID와 이름 및 JSON 데이터를 HTTP로 받는다.")
    void sendEventProducesSseFrameOverHttp(Event eventType, String expectedEventName) throws IOException {
        UUID userId = UUID.randomUUID();
        String token = "sse-test-token";

        prepareAuthenticatedUser(userId, token);

        HttpURLConnection connection = createSseConnection(token);

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            MediaType contentType = MediaType.parseMediaType(connection.getContentType());
            assertThat(contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEqualTo("");

                // 최초 프레임을 받은 뒤 전송하여 emitter가 실제 HTTP 응답에 연결된 상태에서 확인한다.
                // 공통 전송 계약용 payload이며 실제 알림·DM 도메인 API 연동을 검증하는 데이터는 아니다.
                UUID eventId = UUID.randomUUID();
                SseEvent<Map<String, String>> event = new SseEvent<>(
                        eventId,
                        userId,
                        eventType.getName(),
                        Map.of("message", "테스트 알림")
                );

                sseDeliveryService.sendToLocalConnections(event);

                assertThat(reader.readLine()).isEqualTo("id:" + eventId);
                assertThat(reader.readLine()).isEqualTo("event:"+expectedEventName);

                String dataLine = reader.readLine();
                assertThat(dataLine).startsWith("data:");
                // 문자열 표기 순서가 아닌 JSON 구조를 비교한다. 객체가 JSON 문자열로 이중 인코딩되면 실패한다.
                assertThat(objectMapper.readTree(dataLine.substring("data:".length())))
                        .isEqualTo(objectMapper.readTree("""
                                {"message":"테스트 알림"}
                                """));
                assertThat(reader.readLine()).isEqualTo("");
            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connection.disconnect();
            }
        }
    }

    /** 실제 HTTP 요청의 인증·응답 형식과 대기 제한을 공통으로 준비한다. */
    private HttpURLConnection createSseConnection(String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + "/api/sse")
                .toURL()
                .openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        // 연결이나 프레임 수신이 잘못됐을 때 테스트가 열린 스트림을 무한정 기다리지 않게 한다.
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        // 인증 실패가 로그인 페이지 리다이렉트로 바뀌어도 최종 200 응답에 가려지지 않게 한다.
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    /**
     * 토큰 해석 결과와 사용자 조회 결과를 준비한다. 실제 요청은 여전히 JWT 필터와 보안 체인을 통과한다.
     * 이 테스트의 목적은 JWT 서명 검증이 아니라 인증된 HTTP 스트림의 전송 동작이다.
     */
    private void prepareAuthenticatedUser(UUID userId, String token) {
        User user = Mockito.mock(User.class);

        given(user.getId()).willReturn(userId);
        given(user.getEmail()).willReturn("sse-test@example.com");
        given(user.getRole()).willReturn(Role.USER);
        given(user.isLocked()).willReturn(false);

        given(jwtTokenProvider.parseAccessToken(token)).willReturn(userId);
        given(userService.getById(userId)).willReturn(user);
    }

    @Test
    @DisplayName("이벤트의 JSON 변환에 실패해도 같은 SSE 연결로 다음 정상 이벤트를 수신한다.")
    void serializationFailureKeepsConnection() throws IOException {
        UUID userId = UUID.randomUUID();
        String token = "sse-test-token";

        prepareAuthenticatedUser(userId, token);
        HttpURLConnection connection = createSseConnection(token);
        SseEmitter registeredEmitter = null;

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                // 최초 프레임을 읽어 실제 HTTP 응답이 시작된 연결에서 변환 실패를 재현한다.
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                Map<Connection, SseEmitter> connections = sseEmitterRepository.findAllByUserId(userId);
                assertThat(connections).hasSize(1);
                registeredEmitter = connections.values().iterator().next();

                SseEvent<SerializationFailingPayload> event = new SseEvent<>(
                        UUID.randomUUID(),
                        userId,
                        Event.NOTIFICATIONS.getName(),
                        new SerializationFailingPayload()
                );

                assertThatCode(()-> sseDeliveryService.sendToLocalConnections(event))
                        .doesNotThrowAnyException();

                // 실패한 이벤트를 건너뛰어도 저장소에는 동일한 연결이 유지되어야 한다.
                assertThat(sseEmitterRepository.findAllByUserId(userId).values())
                        .containsExactly(registeredEmitter);

                UUID nextEventId = UUID.randomUUID();
                SseEvent<Map<String, String>> nextEvent = new SseEvent<>(
                        nextEventId,
                        userId,
                        Event.NOTIFICATIONS.getName(),
                        Map.of("message", "정상 알림")
                );

                sseDeliveryService.sendToLocalConnections(nextEvent);

                // 기존 reader에서 다음 프레임을 바로 읽어 실패한 이벤트의 불완전한 프레임이 남지 않았는지도 확인한다.
                assertThat(reader.readLine()).isEqualTo("id:" + nextEventId);
                assertThat(reader.readLine()).isEqualTo("event:notifications");

                String dataLine = reader.readLine();
                assertThat(dataLine).startsWith("data:");
                assertThat(objectMapper.readTree(dataLine.substring("data:".length())))
                        .isEqualTo(objectMapper.readTree("""
                                {"message":"정상 알림"}
                                """));
                assertThat(reader.readLine()).isEmpty();
            }
        } finally {
            try {
                if (registeredEmitter != null){
                    registeredEmitter.complete();
                }
            }finally {
                connection.disconnect();
            }
        }
    }


    public static class SerializationFailingPayload{
        public String getMessage(){
            throw new IllegalStateException("테스트용 JSON 변환 실패");
        }
    }
}
