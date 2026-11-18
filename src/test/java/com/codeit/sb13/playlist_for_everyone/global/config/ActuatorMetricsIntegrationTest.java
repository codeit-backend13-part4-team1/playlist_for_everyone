package com.codeit.sb13.playlist_for_everyone.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
class ActuatorMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}