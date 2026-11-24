package com.codeit.sb13.playlist_for_everyone.domain.auth.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.request.ResetPasswordRequest;
import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.RefreshTokenCookieFactory;
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
 * <p>로그인·로그아웃은 Spring Security 필터가 처리해 springdoc이 수집하지 않으므로
 * {@link AuthFilterOpenApiCustomizer}가 같은 "인증" 태그로 추가한다.
 * 인증 API는 로그인 전에도 호출하므로 bearerAuth를 붙이지 않는다.
 */
@Tag(name = "인증", description = "로그인, 로그아웃, 소셜 로그인, 토큰 재발급, 비밀번호 초기화 API입니다.")
@ApiResponses({
        @ApiResponse(responseCode = "500", description = "서버 오류",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public interface AuthApi {

    @Operation(summary = "CSRF 토큰 발급", description = "XSRF-TOKEN 쿠키를 발급합니다. POST·PATCH 요청 전에 호출합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "발급 성공")
    })
    ResponseEntity<Void> issueCsrfToken(@Parameter(hidden = true) CsrfToken csrfToken);

    @Operation(summary = "토큰 재발급",
            description = "REFRESH_TOKEN 쿠키로 Access Token을 다시 발급하고 Refresh Token 쿠키를 교체합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "Refresh Token 없음 또는 만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<JwtDto> reissue(
            @Parameter(description = "브라우저가 자동으로 보내는 Refresh Token 쿠키")
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken);

    @Operation(summary = "비밀번호 초기화",
            description = "3분간 쓸 수 있는 임시 비밀번호를 메일로 보냅니다. 소셜 가입 계정은 사용할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "발급 완료. 메일은 잠시 후 발송됩니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 소셜 가입 계정",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "가입하지 않은 이메일",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "요청 횟수 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request);
}