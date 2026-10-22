package com.codeit.sb13.playlist_for_everyone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("요청 로그 MDC 인터셉터")
class MDCLoggingInterceptorTest {

    private final MDCLoggingInterceptor interceptor = new MDCLoggingInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Object handler = new Object();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 정보를 MDC에 기록하고 요청마다 새로운 UUID를 생성한다")
    void recordsRequestMetadataAndGeneratesANewIdForEachRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/playlists");
        request.setRemoteAddr("192.0.2.10");

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        String firstRequestId = MDC.get(MDCLoggingInterceptor.REQUEST_ID);
        assertThat(UUID.fromString(firstRequestId).toString()).isEqualTo(firstRequestId);
        assertThat(MDC.get(MDCLoggingInterceptor.REQUEST_METHOD)).isEqualTo("POST");
        assertThat(MDC.get(MDCLoggingInterceptor.REQUEST_URI)).isEqualTo("/api/playlists");
        assertThat(MDC.get(MDCLoggingInterceptor.REQUEST_IP)).isEqualTo("192.0.2.10");
        interceptor.afterCompletion(request, response, handler, null);

        MockHttpServletRequest nextRequest = new MockHttpServletRequest("GET", "/api/contents");
        interceptor.preHandle(nextRequest, response, handler);

        String nextRequestId = MDC.get(MDCLoggingInterceptor.REQUEST_ID);
        assertThat(UUID.fromString(nextRequestId).toString()).isEqualTo(nextRequestId);
        assertThat(nextRequestId).isNotEqualTo(firstRequestId);
        assertThat(MDC.get(MDCLoggingInterceptor.REQUEST_METHOD)).isEqualTo("GET");
        assertThat(MDC.get(MDCLoggingInterceptor.REQUEST_URI)).isEqualTo("/api/contents");
    }

    @ParameterizedTest(name = "예외 발생 여부: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("요청 종료 시 예외 발생 여부와 관계없이 MDC를 비운다")
    void clearsMdcAfterCompletion(boolean failed) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contents");
        interceptor.preHandle(request, response, handler);
        Exception exception = failed ? new IllegalStateException("요청 실패") : null;

        interceptor.afterCompletion(request, response, handler, exception);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
