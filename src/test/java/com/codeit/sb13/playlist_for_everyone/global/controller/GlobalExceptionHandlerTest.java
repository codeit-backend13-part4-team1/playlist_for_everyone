package com.codeit.sb13.playlist_for_everyone.global.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.WebMvcConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.testsupport.exception.TestEveryOneException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class,
        WebMvcConfig.class, MDCLoggingInterceptor.class})
@DisplayName("글로벌 예외 응답")
class GlobalExceptionHandlerTest {

    private static final String BASE = "/test/errors";
    private static final String INTERNAL_DETAIL = "internal-database-connection-detail";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "INVALID_REQUEST, 400, 요청 데이터가 올바르지 않습니다.",
            "INTERNAL_SERVER_ERROR, 500, 서버 내부 오류"
    })
    @DisplayName("공통 예외는 오류 코드에 지정된 HTTP 상태와 메시지 및 상세 정보를 반환한다")
    void mapsCommonExceptionsToTheApiContract(String code, int statusCode, String message) throws Exception {
        expectError(mockMvc.perform(get(BASE + "/business/{code}", code)), statusCode,
                "TestEveryOneException", message)
                .andExpect(jsonPath("$.details", aMapWithSize(1)))
                .andExpect(jsonPath("$.details.operation").value("test-operation"));
    }

    @Test
    @DisplayName("null 상세 정보는 빈 객체로 반환하고 원인 예외의 내부 메시지는 노출하지 않는다")
    void rendersNullDetailsAsAnEmptyObjectAndDoesNotExposeTheCause() throws Exception {
        expectError(mockMvc.perform(get(BASE + "/caused")), 400,
                "TestEveryOneException", "요청 데이터가 올바르지 않습니다.")
                .andExpect(jsonPath("$.details", aMapWithSize(0)))
                .andExpect(content().string(not(containsString(INTERNAL_DETAIL))));
    }

    @ParameterizedTest(name = "{0} → 400")
    @MethodSource("badRequests")
    @DisplayName("잘못된 요청 형식이나 바인딩 및 제약 조건 오류는 400 응답으로 반환한다")
    void mapsMalformedRequestsToBadRequest(String exceptionName, MockHttpServletRequestBuilder request) throws Exception {
        expectError(mockMvc.perform(request), 400, exceptionName, "요청 데이터가 올바르지 않습니다.")
                .andExpect(jsonPath("$.details", aMapWithSize(0)));
    }

    static Stream<Arguments> badRequests() {
        return Stream.of(
                Arguments.of("HttpMessageNotReadableException", post(BASE + "/body")
                        .contentType(MediaType.APPLICATION_JSON).content("{invalid-json")),
                Arguments.of("MethodArgumentTypeMismatchException", get(BASE + "/argument").param("limit", "abc")),
                Arguments.of("MissingServletRequestParameterException", get(BASE + "/argument")),
                Arguments.of("MissingRequestHeaderException", get(BASE + "/header")),
                Arguments.of("ConstraintViolationException", get(BASE + "/constraint")),
                Arguments.of("BindException", get(BASE + "/binding"))
        );
    }

    @Test
    @DisplayName("입력값 검증 실패 시 모든 오류 메시지를 필드별로 묶어 400 응답에 포함한다")
    void groupsAllValidationMessagesByField() throws Exception {
        expectError(mockMvc.perform(post(BASE + "/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"", "email":"invalid-email"}
                                """)),
                400, "MethodArgumentNotValidException", "요청 데이터가 올바르지 않습니다.")
                .andExpect(jsonPath("$.details", aMapWithSize(2)))
                .andExpect(jsonPath("$.details.name", containsInAnyOrder("이름은 필수입니다.", "이름은 2자 이상이어야 합니다.")))
                .andExpect(jsonPath("$.details.email", containsInAnyOrder("이메일 형식이 올바르지 않습니다.")));
    }

    @Test
    @DisplayName("입력값 검증을 통과한 요청은 200 상태와 정상 본문을 반환한다")
    void acceptsAValidRequest() throws Exception {
        mockMvc.perform(post(BASE + "/body").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"테스터", "email":"tester@example.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스터"));
    }

    @Test
    @DisplayName("매핑되지 않은 경로 요청은 404 상태와 리소스 미존재 메시지를 반환한다")
    void preservesNotFoundForAnUnmappedPath() throws Exception {
        expectError(mockMvc.perform(get(BASE + "/does-not-exist")), 404,
                "NoResourceFoundException", "요청한 리소스를 찾을 수 없습니다.")
                .andExpect(jsonPath("$.details", aMapWithSize(0)));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 요청은 405 상태와 Allow 헤더를 반환한다")
    void preservesMethodNotAllowedAndTheAllowHeader() throws Exception {
        expectError(mockMvc.perform(get(BASE + "/body")), 405,
                "HttpRequestMethodNotSupportedException", "지원하지 않는 HTTP 메서드입니다.")
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"));
    }

    @Test
    @DisplayName("JSON 오류 응답을 수신할 수 있으면 응답 형식 불일치 예외의 406 상태를 유지한다")
    void preservesNotAcceptableWhenTheErrorResponseCanBeWrittenAsJson() throws Exception {
        // 예외 응답의 JSON 직렬화가 가능한 Accept 헤더로 상태 변환을 검증한다.
        expectError(mockMvc.perform(get(BASE + "/not-acceptable").accept(MediaType.APPLICATION_JSON)), 406,
                "HttpMediaTypeNotAcceptableException", "요청한 응답 형식을 제공할 수 없습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 요청 Content-Type은 415 상태와 안내 메시지를 반환한다")
    void preservesUnsupportedMediaType() throws Exception {
        expectError(mockMvc.perform(post(BASE + "/body")
                        .contentType(MediaType.TEXT_PLAIN).content("plain text")), 415,
                "HttpMediaTypeNotSupportedException", "지원하지 않는 요청 데이터 형식입니다.");
    }

    @ParameterizedTest(name = "Spring ErrorResponse의 {0} 상태를 유지한다")
    @CsvSource({"429, 요청 데이터가 올바르지 않습니다.", "503, 서버 내부 오류"})
    @DisplayName("그 밖의 Spring 오류는 지정된 HTTP 상태를 유지하고 내부 메시지를 노출하지 않는다")
    void preservesOtherSpringErrorStatusesWithoutExposingInternalDetails(int statusCode, String message) throws Exception {
        expectError(mockMvc.perform(get(BASE + "/status/{status}", statusCode)), statusCode,
                "ResponseStatusException", message)
                .andExpect(jsonPath("$.details", aMapWithSize(0)))
                .andExpect(content().string(not(containsString(INTERNAL_DETAIL))));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 상태와 공통 오류 메시지로 반환하고 내부 메시지를 숨긴다")
    void masksUnexpectedServerErrorMessages() throws Exception {
        expectError(mockMvc.perform(get(BASE + "/unexpected")), 500,
                "IllegalStateException", "서버 내부 오류")
                .andExpect(jsonPath("$.details", aMapWithSize(0)))
                .andExpect(content().string(not(containsString(INTERNAL_DETAIL))));
    }

    @Test
    @DisplayName("접근 권한이 없으면 403 상태와 공통 메시지를 반환하고 내부 메시지를 숨긴다")
    void mapsAccessDeniedToForbidden() throws Exception {
        expectError(mockMvc.perform(get(BASE + "/access-denied")), 403,
            "AccessDeniedException", "접근 권한이 없습니다.")
            .andExpect(jsonPath("$.details", aMapWithSize(0)))
            .andExpect(content().string(not(containsString(INTERNAL_DETAIL))));
    }

    private ResultActions expectError(ResultActions result, int statusCode, String exceptionName, String message) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.exceptionName").value(exceptionName))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.details").isMap());
    }

    @TestComponent
    @RestController
    @RequestMapping(BASE)
    static class TestController {

        @GetMapping("/business/{code}")
        void business(@PathVariable("code") ApiErrorCode code) {
            throw new TestEveryOneException(code, Map.of("operation", "test-operation"));
        }

        @GetMapping("/caused")
        void caused() {
            throw new TestEveryOneException(ApiErrorCode.INVALID_REQUEST, null,
                    new IllegalStateException(INTERNAL_DETAIL));
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        ValidatedRequest body(@Valid @RequestBody ValidatedRequest request) {
            return request;
        }

        @GetMapping("/argument")
        int argument(@RequestParam("limit") int limit) {
            return limit;
        }

        @GetMapping("/header")
        String requiredHeader(@RequestHeader("X-Required") String header) {
            return header;
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException("제약 조건 위반", Set.of());
        }

        @GetMapping("/binding")
        void binding() throws BindException {
            throw new BindException(new BeanPropertyBindingResult(new Object(), "request"));
        }

        @GetMapping("/not-acceptable")
        void notAcceptable() throws HttpMediaTypeNotAcceptableException {
            throw new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_XML));
        }

        @GetMapping("/status/{status}")
        void springError(@PathVariable("status") int status) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(status), INTERNAL_DETAIL);
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(INTERNAL_DETAIL);
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException(INTERNAL_DETAIL);
        }

    }

    record ValidatedRequest(
            @NotBlank(message = "이름은 필수입니다.")
            @Size(min = 2, message = "이름은 2자 이상이어야 합니다.") String name,
            @Email(message = "이메일 형식이 올바르지 않습니다.") String email
    ) {
    }
}
