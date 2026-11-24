package com.codeit.sb13.playlist_for_everyone.domain.auth.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 인증 API 중 컨트롤러가 처리하는 API의 Swagger 계약.
 *
 * <p>로그인·로그아웃·소셜 로그인 시작은 Spring Security 필터가 처리해 springdoc이 자동으로 수집하지 않는다.
 * 이 경로들은 별도의 OpenApiCustomizer가 같은 "인증" 태그로 문서에 추가한다.
 * 인증 API는 모두 로그인 전에도 호출할 수 있어 bearerAuth를 붙이지 않는다.
 */
@Tag(name = "인증", description = """
        로그인, 로그아웃, 토큰 재발급, 비밀번호 초기화, 소셜 로그인 API입니다.\n
        로그인 흐름: GET /api/auth/csrf-token → POST /api/auth/sign-in → 응답의 accessToken을 Authorization: Bearer 헤더로 사용합니다.\n
        Access Token이 만료되면 POST /api/auth/refresh로 다시 발급받습니다. Refresh Token은 HttpOnly 쿠키로만 주고받습니다.\n
        POST 요청은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 합니다.
        """)
@ApiResponses({
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public interface AuthApi {

    @Operation(summary = "CSRF 토큰 발급", description = """
            XSRF-TOKEN 쿠키를 발급합니다. 응답 본문은 없습니다.\n
            로그인·회원가입 등 POST·PATCH·DELETE 요청 전에 호출합니다.
            """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "발급 성공 (Set-Cookie: XSRF-TOKEN)")
    })
    ResponseEntity<Void> issueCsrfToken(@Parameter(hidden = true) CsrfToken csrfToken);

    @Operation(summary = "토큰 재발급", description = """
            REFRESH_TOKEN 쿠키로 새 Access Token과 Refresh Token을 발급합니다.\n
            Access Token은 응답 본문으로, 새 Refresh Token은 쿠키로 교체되며 기존 Refresh Token은 더 이상 쓸 수 없습니다.\n
            소셜 로그인 후에는 이 API로 Access Token을 받습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "재발급 성공 (Set-Cookie: REFRESH_TOKEN)"),
            @ApiResponse(responseCode = "401",
                    description = "쿠키가 없거나, 만료·폐기된 Refresh Token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "CSRF 토큰이 없거나 일치하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<JwtDto> reissue(
            @Parameter(description = "로그인할 때 발급된 Refresh Token. 브라우저가 쿠키로 자동 전송합니다.")
            @CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken);

    @Operation(summary = "비밀번호 초기화", description = """
            3분 동안 사용할 수 있는 임시 비밀번호를 이메일로 보냅니다. 기존 비밀번호는 바뀌지 않습니다.\n
            같은 계정은 1분에 한 번, 1시간에 최대 5번까지 요청할 수 있습니다.\n
            소셜 로그인으로 가입한 계정은 비밀번호가 없어 초기화할 수 없습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204",
                    description = "임시 비밀번호 메일 발송 성공"),
            @ApiResponse(responseCode = "400",
                    description = "이메일 형식 오류, 또는 소셜 로그인 가입 계정 (A_002)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "CSRF 토큰이 없거나 일치하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "가입하지 않은 이메일 (U_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429",
                    description = "요청 횟수 초과 (A_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request);
}
