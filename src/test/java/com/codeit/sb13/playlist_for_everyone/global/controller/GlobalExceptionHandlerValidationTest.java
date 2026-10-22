package com.codeit.sb13.playlist_for_everyone.global.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@DisplayName("글로벌 예외 처리의 검증 오류 상세 정보")
class GlobalExceptionHandlerValidationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("필드 및 객체 검증 오류를 모두 포함하고 메시지가 없으면 기본 안내를 사용한다")
    void includesGlobalErrorsAndUsesAFallbackForMissingMessages() throws NoSuchMethodException {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "request");
        errors.addError(new ObjectError("request", "두 값이 일치해야 합니다."));
        errors.addError(new ObjectError("request", null));
        errors.addError(new FieldError("request", "name", "이름은 필수입니다."));
        errors.addError(new FieldError("request", "name", null));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerValidationTest.class.getDeclaredMethod("accept", Object.class), 0);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(
                new MethodArgumentNotValidException(parameter, errors));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.exceptionName()).isEqualTo("MethodArgumentNotValidException");
        assertThat(body.message()).isEqualTo("요청 데이터가 올바르지 않습니다.");
        assertThat(body.details()).containsOnlyKeys("request", "name");
        assertThat(body.details().get("request")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("두 값이 일치해야 합니다.", "상세 내용이 없습니다.");
        assertThat(body.details().get("name")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("이름은 필수입니다.", "상세 내용이 없습니다.");
    }

    private void accept(Object request) {
    }
}
