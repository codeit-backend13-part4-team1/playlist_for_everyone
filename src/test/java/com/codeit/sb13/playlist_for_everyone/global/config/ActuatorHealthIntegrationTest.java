package com.codeit.sb13.playlist_for_everyone.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * 실제 Actuator 엔드포인트와 보안 설정을 함께 띄워 ALB 헬스 체크 경로를 검증한다.
 *
 * <p>DB는 test 프로필의 H2를 사용한다. 테스트 환경에는 Redis가 없으므로
 * Redis 헬스 지표만 제외한다. 이 지표를 켜 두면 연결 실패로 503(DOWN)이 된다.
 */
@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@DisplayName("Actuator 헬스 체크 통합 테스트")
class ActuatorHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("헬스 체크는 인증 없이 UP 상태를 반환한다.")
    void 헬스_체크는_인증_없이_UP_상태를_반환한다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("헬스 체크 외 Actuator는 인증 없이 접근할 수 없다.")
    void 헬스_체크_외_액추에이터는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

}
