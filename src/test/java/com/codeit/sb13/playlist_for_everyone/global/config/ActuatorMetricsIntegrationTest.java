package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistorySnapshot;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
class ActuatorMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseConnectionService connectionService;

    @MockitoBean
    private UserSessionRepository sessionRepository;

    @MockitoBean
    private SseHistoryStore historyStore;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_메트릭_목록을_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_사용자는_메트릭을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비로그인_사용자는_메트릭을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_사용자는_HEAD로도_메트릭을_조회할_수_없다() throws Exception {
        mockMvc.perform(head("/actuator/metrics"))
                .andExpect(status().isForbidden());

        mockMvc.perform(head("/actuator/metrics/spring.batch.job"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_활성_SSE_연결이_없으면_0을_조회한다() throws Exception {
        assertActiveConnections(0.0);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 같은_사용자의_SSE_연결을_열고_닫으면_활성_연결_수가_바뀐다() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginSession session = new LoginSession(UUID.randomUUID());

        when(sessionRepository.isCurrent(userId, session.id())).thenReturn(true);
        when(historyStore.snapshot(userId, null))
                .thenReturn(new SseHistorySnapshot(null, List.of()));

        try ( var ignored = mockConstruction(SseEmitter.class)){
            assertActiveConnections(0.0);

            SseEmitter first = connectionService.connect(userId, null, session);
            assertActiveConnections(1.0);
            SseEmitter second = connectionService.connect(userId, null, session);
            assertActiveConnections(2.0);

            Runnable completeFirst = captureCompletion(first);
            completeFirst.run();
            assertActiveConnections(1.0);

            completeFirst.run();
            assertActiveConnections(1.0);

            captureCompletion(second).run();
            assertActiveConnections(0.0);
        } finally {
            connectionService.closeAllLocalConnections();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 타임아웃과_오류로_종료된_SSE_연결은_활성_수에서_제외된다() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginSession session = new LoginSession(UUID.randomUUID());

        when(sessionRepository.isCurrent(userId, session.id())).thenReturn(true);
        when(historyStore.snapshot(userId, null))
                .thenReturn(new SseHistorySnapshot(null, List.of()));

        try ( var ignored = mockConstruction(SseEmitter.class)){
            assertActiveConnections(0.0);

            SseEmitter first = connectionService.connect(userId, null, session);
            assertActiveConnections(1.0);
            SseEmitter second = connectionService.connect(userId, null, session);
            assertActiveConnections(2.0);

            ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
            verify(first).onTimeout(timeout.capture());
            timeout.getValue().run();
            assertActiveConnections(1.0);

            ArgumentCaptor<Consumer<Throwable>> error = ArgumentCaptor.captor();
            verify(second).onError(error.capture());
            error.getValue().accept(new IOException("연결 오류"));
            assertActiveConnections(0.0);
        } finally {
            connectionService.closeAllLocalConnections();
        }
    }

    private void assertActiveConnections(double expected) throws Exception {
        mockMvc.perform(get("/actuator/metrics/everyone.sse.connections.active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].statistic").value("VALUE"))
                .andExpect(jsonPath("$.measurements[0].value").value(expected));
    }

    private Runnable captureCompletion(SseEmitter emitter){
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        verify(emitter).onCompletion(captor.capture());
        return captor.getValue();
    }
}