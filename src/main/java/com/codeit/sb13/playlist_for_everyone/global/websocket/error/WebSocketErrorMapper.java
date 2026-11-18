package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 예외를 HTTP와 같은 exceptionName·message·details 구조로 변환한다.
 *
 * <p>MessageDeliveryException 래퍼만 벗겨 업무 예외의 코드·상세값을 보존한다.
 * 그 밖의 예외는 정해진 메시지로 분류하여 내부 예외 메시지를 그대로 노출하지 않는다.
 * 이 컴포넌트는 전송이나 연결 종료를 수행하지 않는다.
 */
@Component
public class WebSocketErrorMapper {

    public ApiErrorResponse toErrorResponse(Throwable exception) {
        Throwable current = exception;

        while (current instanceof MessageDeliveryException
                && current.getCause() != null) {
            current = current.getCause();
        }

        if (current instanceof EveryOneException domainException) {
            return new ApiErrorResponse(
                    domainException.getClass().getSimpleName(),
                    domainException.getApiErrorCode().getMessage(),
                    domainException.getDetails()
            );
        }

        if (current instanceof AuthenticationException
                || current instanceof JwtException) {
            return new ApiErrorResponse(
                    current.getClass().getSimpleName(),
                    "유효하지 않은 인증 정보입니다.",
                    Map.of()
            );
        }

        if (current instanceof AccessDeniedException) {
            return new ApiErrorResponse(
                    current.getClass().getSimpleName(),
                    "접근 권한이 없습니다.",
                    Map.of()
            );
        }

        if (current instanceof MessageConversionException) {
            return new ApiErrorResponse(
                    current.getClass().getSimpleName(),
                    ApiErrorCode.INVALID_REQUEST.getMessage(),
                    Map.of()
            );
        }

        if (current instanceof MethodArgumentNotValidException validationException) {
            return new ApiErrorResponse(
                    current.getClass().getSimpleName(),
                    ApiErrorCode.INVALID_REQUEST.getMessage(),
                    getValidationDetails(validationException)
            );
        }

        return new ApiErrorResponse(
                current.getClass().getSimpleName(),
                ApiErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                Map.of()
        );
    }

    /** 같은 필드의 여러 검증 오류를 리스트로 보존하고, 필드가 없는 오류는 객체 이름으로 구분한다. */
    private Map<String, Object> getValidationDetails(MethodArgumentNotValidException exception) {
        BindingResult bindingResult = exception.getBindingResult();
        if (bindingResult == null) {
            return Map.of();
        }

        Map<String, List<String>> errors = new LinkedHashMap<>();

        for (ObjectError error : bindingResult.getAllErrors()) {
            String key = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : error.getObjectName();
            String message = error.getDefaultMessage() == null
                    ? "상세 내용이 없습니다."
                    : error.getDefaultMessage();

            errors.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(message);
        }

        return new LinkedHashMap<>(errors);
    }
}
