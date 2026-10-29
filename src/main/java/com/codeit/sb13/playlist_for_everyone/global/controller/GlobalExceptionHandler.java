package com.codeit.sb13.playlist_for_everyone.global.controller;

import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EveryOneException.class)
    public ResponseEntity<ApiErrorResponse> handleEveryOneException(EveryOneException e){
        ApiErrorCode apiErrorCode = e.getApiErrorCode();
        logException(e, apiErrorCode.getStatus());

        return ResponseEntity.status(apiErrorCode.getStatus())
                .body(new ApiErrorResponse(e.getClass().getSimpleName(), apiErrorCode.getMessage(), e.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e){
        Map<String, Object> details = getValidationDetails(e);

        log.warn("요청 데이터 검증에 실패했습니다. details={}", details);

        ApiErrorCode apiErrorCode = ApiErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(apiErrorCode.getStatus())
                .body(new ApiErrorResponse(e.getClass().getSimpleName(), apiErrorCode.getMessage(), details));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            ConstraintViolationException.class,
            BindException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception e){
        ApiErrorCode apiErrorCode = ApiErrorCode.INVALID_REQUEST;
        log.warn("잘못된 요청 형식입니다. errorCode={}, exceptionType={}, message={}",
                apiErrorCode,
                e.getClass().getSimpleName(),
                e.getMessage());

        return ResponseEntity.status(apiErrorCode.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(e.getClass().getSimpleName(), apiErrorCode.getMessage(), Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("접근 권한이 없습니다. message={}", e.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiErrorResponse(e.getClass().getSimpleName(), "접근 권한이 없습니다.", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {

        // Spring이 HTTP 상태를 지정한 예외라면 해당 상태를 유지합니다.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();

            String message = switch (status.value()){
                case 404 -> "요청한 리소스를 찾을 수 없습니다.";
                case 405 -> "지원하지 않는 HTTP 메서드입니다.";
                case 406 -> "요청한 응답 형식을 제공할 수 없습니다.";
                case 415 -> "지원하지 않는 요청 데이터 형식입니다.";
                default -> status.is5xxServerError() ? ApiErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                        : ApiErrorCode.INVALID_REQUEST.getMessage();
            };

            if (status.is5xxServerError()){
                log.error("서버 오류가 발생했습니다. status={}",status.value(),e);
            }else {
                log.warn("요청 처리에 실패 했습니다. status={}, exceptionType={}", status.value(), e.getClass().getSimpleName());

            }
            return  ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(new ApiErrorResponse(
                            e.getClass().getSimpleName(),
                            message,
                            Map.of()
                    ));
        }

        // HTTP 상태가 지정되지 않은 예상 밖의 예외는 500으로 처리합니다.
        log.error("처리되지 않은 서버 오류가 발생했습니다.", e);
        ApiErrorCode apiErrorCode = ApiErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(apiErrorCode.getStatus())
                .body(new ApiErrorResponse(e.getClass().getSimpleName(), apiErrorCode.getMessage(), Map.of()));
    }


    private @NonNull Map<String, Object> getValidationDetails(MethodArgumentNotValidException e) {
        return Collections.unmodifiableMap(
                Stream.concat(
                        e.getBindingResult().getGlobalErrors().stream(),
                        e.getBindingResult().getFieldErrors().stream()
                ).collect(Collectors.groupingBy(
                        this::getValidationErrorKey,
                        Collectors.mapping(this::getValidationErrorDetail, Collectors.toList())
                ))
        );
    }

    private String getValidationErrorKey(ObjectError error) {
        if(error instanceof FieldError field){
            return field.getField();
        }

        return error.getObjectName();
    }

    private String getValidationErrorDetail(ObjectError error){
        return error.getDefaultMessage() == null ? "상세 내용이 없습니다." : error.getDefaultMessage();
    }

    private static void logException(EveryOneException e, HttpStatus status) {
        if (status.is5xxServerError()) {
            log.error("서버 오류가 발생했습니다. errorCode={}, message={}", e.getApiErrorCode(), e.getMessage(), e);
            return;
        }
        if (e.getCause() != null) {
            log.warn("비즈니스 예외가 발생했습니다. errorCode={}, message={}", e.getApiErrorCode(), e.getMessage(), e);
            return;
        }
        log.warn("비즈니스 예외가 발생했습니다. errorCode={}, message={}", e.getApiErrorCode(), e.getMessage());
    }
}
