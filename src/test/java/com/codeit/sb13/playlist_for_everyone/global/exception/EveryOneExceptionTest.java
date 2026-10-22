package com.codeit.sb13.playlist_for_everyone.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.testsupport.exception.TestEveryOneException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공통 예외 기반 동작")
class EveryOneExceptionTest {

    @Test
    @DisplayName("원인 없는 예외는 전달한 오류 코드와 메시지 및 상세 정보를 유지한다")
    void preservesErrorCodeMessageAndDetailsWithoutCause() {
        ApiErrorCode errorCode = ApiErrorCode.INVALID_REQUEST;
        Map<String, Object> details = Map.of("field", "name");

        EveryOneException exception = new TestEveryOneException(errorCode, details);

        assertThat(exception.getApiErrorCode()).isEqualTo(errorCode);
        assertThat(exception).hasMessage(errorCode.getMessage()).hasNoCause();
        assertThat(exception.getDetails()).containsExactlyEntriesOf(details);
    }

    @Test
    @DisplayName("원인을 지정한 예외는 오류 코드와 메시지 및 상세 정보와 원인 예외를 유지한다")
    void preservesErrorCodeMessageDetailsAndCause() {
        ApiErrorCode errorCode = ApiErrorCode.INTERNAL_SERVER_ERROR;
        Map<String, Object> details = Map.of("operation", "test-operation");
        Throwable cause = new IllegalStateException("원인 예외");

        EveryOneException exception = new TestEveryOneException(errorCode, details, cause);

        assertThat(exception.getApiErrorCode()).isEqualTo(errorCode);
        assertThat(exception).hasMessage(errorCode.getMessage()).hasCause(cause);
        assertThat(exception.getDetails()).containsExactlyEntriesOf(details);
    }

    @Test
    @DisplayName("원인 없는 예외의 상세 정보가 null이면 빈 객체로 정규화한다")
    void normalizesNullDetailsWithoutCause() {
        EveryOneException exception = new TestEveryOneException(ApiErrorCode.INVALID_REQUEST, null);

        assertThat(exception.getDetails()).isEmpty();
        assertThat(exception).hasNoCause();
    }

    @Test
    @DisplayName("원인을 지정한 예외의 상세 정보가 null이면 빈 객체로 정규화하고 원인을 유지한다")
    void normalizesNullDetailsWithCause() {
        Throwable cause = new IllegalStateException("원인 예외");

        EveryOneException exception = new TestEveryOneException(ApiErrorCode.INVALID_REQUEST, null, cause);

        assertThat(exception.getDetails()).isEmpty();
        assertThat(exception).hasCause(cause);
    }
}
