package com.codeit.sb13.playlist_for_everyone.domain.user.controller.swagger;


import com.codeit.sb13.playlist_for_everyone.domain.user.dto.request.*;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 사용자 API의 Swagger 계약을 모은 인터페이스.
 *
 * <p>회원가입을 제외한 API에만 bearerAuth를 붙인다. 클래스에 붙이면 메서드에서 제거할 수 없어 메서드별로 지정한다.
 * 관리자 API의 권한은 SecurityConfig의 URL 규칙이, 본인 확인은 서비스의 @PreAuthorize가 검사한다.
 * 성공 응답의 본문 형식은 메서드 반환 타입에서 자동으로 만든다.
 */
@Tag(name = "사용자", description = """
        회원가입, 사용자 조회, 프로필·비밀번호 변경, 관리자용 권한·잠금 변경 API입니다.\n
        회원가입을 제외한 모든 API는 JWT 인증이 필요합니다.\n
        POST·PATCH 요청은 GET /api/auth/csrf-token으로 받은 XSRF-TOKEN 값을 X-XSRF-TOKEN 헤더로 보내야 합니다.
        """)
@ApiResponses({
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)))
})
public interface UserApi {

    @Operation(summary = "회원가입", description = """
            이메일, 이름, 비밀번호로 일반 회원을 생성합니다. 권한은 항상 USER입니다.\n
            이메일은 255자 이하, 이름은 100자 이하, 비밀번호는 UTF-8 기준 72바이트 이하입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 이미 사용 중인 이메일(U_002)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰이 없거나 일치하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest request);

    @Operation(summary = "사용자 목록 조회 (관리자)", description = """
            관리자가 사용자 목록을 커서 방식으로 조회합니다.\n
            이메일 부분 일치(emailLike), 권한(roleEqual), 잠금 여부(isLocked)로 거를 수 있습니다.\n
            다음 페이지는 이전 응답의 nextCursor와 nextIdAfter를 cursor와 idAfter로 함께 보냅니다.\n
            limit은 1~100입니다.
            """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (limit 범위, 정렬 값, cursor 형식 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<CursorResponse<UserDto>> getUsers(@ParameterObject @Valid UserSearchRequest request);

    @Operation(summary = "사용자 상세 조회", description = "사용자 한 명의 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "userId가 UUID 형식이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (U_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserDto> getUser(
            @Parameter(description = "조회할 사용자 ID", required = true) @PathVariable("userId") UUID userId);

    @Operation(summary = "프로필 변경", description = """
            본인의 이름과 프로필 이미지를 변경합니다. multipart/form-data로 요청합니다.\n
            request 파트(JSON)는 필수이며 name은 100자 이하입니다.\n
            image 파트는 선택이며, 보내지 않으면 기존 이미지를 유지합니다.
            """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 지원하지 않는 이미지 형식",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인이 아님 또는 CSRF 토큰 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (U_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserDto> updateProfile(
            @Parameter(description = "변경할 사용자 ID (본인)", required = true) @PathVariable("userId") UUID userId,
            // Swagger UI가 이 파트를 JSON으로 보내도록 지정한다. 없으면 415가 날 수 있다.
            @Parameter(description = "변경할 이름", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            @Valid @RequestPart("request") UserUpdateRequest request,
            @Parameter(description = "새 프로필 이미지 (선택)")
            @RequestPart(value = "image", required = false) MultipartFile image);

    @Operation(summary = "권한 변경 (관리자)", description = """
            사용자의 권한(USER, ADMIN)을 변경합니다.\n
            권한이 실제로 바뀌면 대상 사용자는 강제 로그아웃되고 알림을 받습니다.\n
            현재와 같은 권한으로 요청하면 변경 없이 204를 반환합니다.
            """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공 (또는 이미 같은 권한)"),
            @ApiResponse(responseCode = "400", description = "권한 값이 없거나 올바르지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음 또는 CSRF 토큰 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (U_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> updateRole(
            @Parameter(description = "권한을 변경할 사용자 ID", required = true) @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserRoleUpdateRequest request);

    @Operation(summary = "계정 잠금 변경 (관리자)", description = """
            사용자 계정을 잠그거나(locked: true) 잠금을 해제합니다(locked: false).\n
            잠그면 대상 사용자는 강제 로그아웃되고 다시 로그인할 수 없습니다.\n
            현재와 같은 상태로 요청하면 변경 없이 204를 반환합니다.
            """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공 (또는 이미 같은 상태)"),
            @ApiResponse(responseCode = "400", description = "locked 값이 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음 또는 CSRF 토큰 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (U_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> updateLocked(
            @Parameter(description = "잠금 상태를 변경할 사용자 ID", required = true) @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserLockUpdateRequest request);

    @Operation(summary = "비밀번호 변경", description = """
            본인의 비밀번호를 변경합니다. 비밀번호는 UTF-8 기준 72바이트 이하입니다.\n
            변경하면 아직 사용하지 않은 임시 비밀번호는 모두 폐기됩니다.
            """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "비밀번호가 없거나 72바이트 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인이 아님 또는 CSRF 토큰 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (U_001)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> changePassword(
            @Parameter(description = "비밀번호를 변경할 사용자 ID (본인)", required = true) @PathVariable("userId") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request);
}
