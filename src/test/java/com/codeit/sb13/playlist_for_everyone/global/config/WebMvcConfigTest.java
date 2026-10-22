package com.codeit.sb13.playlist_for_everyone.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.testsupport.exception.TestEveryOneException;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = WebMvcConfigTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfigTest.TestController.class, WebMvcConfig.class,
        MDCLoggingInterceptor.class, GlobalExceptionHandler.class})
@DisplayName("MVC 인터셉터 등록 및 MDC 정리")
class WebMvcConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MVC 요청에 MDC 인터셉터가 적용되고 각 정상 요청 종료 후 MDC를 비운다")
    void registersTheInterceptorAndCleansUpAfterEachSuccessfulRequest() throws Exception {
        MvcResult first = mockMvc.perform(get("/test/mdc")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.20");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.requestMethod").value("GET"))
                .andExpect(jsonPath("$.requestUri").value("/test/mdc"))
                .andExpect(jsonPath("$.requestIp").value("192.0.2.20"))
                .andReturn();
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.requestId");
        assertThat(UUID.fromString(firstId).toString()).isEqualTo(firstId);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        MvcResult second = mockMvc.perform(get("/test/mdc"))
                .andExpect(status().isOk()).andReturn();
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.requestId");

        assertThat(UUID.fromString(secondId).toString()).isEqualTo(secondId);
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("글로벌 예외 핸들러가 예외를 처리한 요청도 종료 후 MDC를 비운다")
    void cleansUpAfterAnExceptionHandledByControllerAdvice() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/mdc/error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("TestEveryOneException"))
                .andReturn();
        String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.details.requestId");

        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @TestComponent
    @RestController
    static class TestController {

        @GetMapping("/test/mdc")
        Map<String, String> snapshot() {
            return MDC.getCopyOfContextMap();
        }

        @GetMapping("/test/mdc/error")
        void error() {
            throw new TestEveryOneException(ApiErrorCode.INVALID_REQUEST,
                    Map.of("requestId", MDC.get(MDCLoggingInterceptor.REQUEST_ID)));
        }
    }
}
