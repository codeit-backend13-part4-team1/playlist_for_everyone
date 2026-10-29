package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SseConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
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
import static org.mockito.BDDMockito.given;

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
@ImportAutoConfiguration({
        TomcatServletWebServerAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
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
                assertThat(reader.readLine()).isEqualTo(":connected");
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

    @ParameterizedTest(name = "{0} 이벤트는 {1} 이름으로 전달된다.")
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

    private HttpURLConnection createSseConnection(String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + "/api/sse")
                .toURL()
                .openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    private void prepareAuthenticatedUser(UUID userId, String token) {
        User user = Mockito.mock(User.class);

        given(user.getId()).willReturn(userId);
        given(user.getEmail()).willReturn("sse-test@example.com");
        given(user.getRole()).willReturn(Role.USER);
        given(user.isLocked()).willReturn(false);

        given(jwtTokenProvider.parseAccessToken(token)).willReturn(userId);
        given(userService.getById(userId)).willReturn(user);
    }

}
