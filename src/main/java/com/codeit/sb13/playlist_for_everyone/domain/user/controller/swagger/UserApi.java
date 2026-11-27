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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 사용자 API의 Swagger 계약을 모은 인터페이스.
 *
 * <p>회원가입을 제외한 API에만 bearerAuth를 붙인다. 클래스에 붙이면 메서드에서 제거할 수 없어 메서드별로 지정한다.
 * 성공 응답의 본문 형식은 메서드 반환 타입에서 자동으로 만든다.
 */
@Tag(name = "사용자", description = """
        회원가입, 사용자 조회, 프로필·비밀번호 변경, 관리자용 권한·잠금 변경 API입니다.\n
        회원가입을 제외한 모든 API는 JWT 인증이 필요합니다.
        """)
@ApiResponses({
        @ApiResponse(responseCode = "500", description = "서버 오류",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public interface UserApi {

    @Operation(summary = "회원가입", description = "일반 회원(USER)을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 이메일 중복",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest request);

    @Operation(summary = "사용자 목록 조회 (관리자)",
            description = "이메일·권한·잠금 상태로 거를 수 있고, 다음 페이지는 nextCursor·nextIdAfter를 cursor·idAfter로 보냅니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<CursorResponse<UserDto>> getUsers(@ParameterObject @Valid UserSearchRequest request);

    @Operation(summary = "사용자 상세 조회")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserDto> getUser(@Parameter(description = "사용자 ID") @PathVariable("userId") UUID userId);

    @Operation(summary = "프로필 변경",
            description = "request(JSON)는 필수, image는 선택이며 보내지 않으면 기존 이미지를 유지합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 지원하지 않는 이미지",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 아님 또는 CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserDto> updateProfile(
            @Parameter(description = "사용자 ID (본인)") @PathVariable("userId") UUID userId,
            // Swagger UI가 이 파트를 JSON으로 보내도록 지정한다. 없으면 415가 날 수 있다.
            @Parameter(description = "변경할 이름", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            @Valid @RequestPart("request") UserUpdateRequest request,
            @Parameter(description = "새 프로필 이미지 (선택)")
            @RequestPart(value = "image", required = false) MultipartFile image);

    @Operation(summary = "권한 변경 (관리자)", description = "권한이 바뀌면 대상 사용자는 강제 로그아웃됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공 (이미 같은 권한이어도 204)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음 또는 CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> updateRole(@Parameter(description = "사용자 ID") @PathVariable("userId") UUID userId,
                                    @Valid @RequestBody UserRoleUpdateRequest request);

    @Operation(summary = "계정 잠금 변경 (관리자)", description = "잠그면 대상 사용자는 강제 로그아웃됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공 (이미 같은 상태여도 204)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음 또는 CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> updateLocked(@Parameter(description = "사용자 ID") @PathVariable("userId") UUID userId,
                                      @Valid @RequestBody UserLockUpdateRequest request);

    @Operation(summary = "비밀번호 변경", description = "본인의 비밀번호를 변경하고, 사용하지 않은 임시 비밀번호를 폐기합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 아님 또는 CSRF 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> changePassword(@Parameter(description = "사용자 ID (본인)") @PathVariable("userId") UUID userId,
                                        @Valid @RequestBody ChangePasswordRequest request);

    @GetMapping("/{watcherId}/watching-sessions")
    void getWatchingSessionByUser(
            @PathVariable UUID watcherId
    );
}