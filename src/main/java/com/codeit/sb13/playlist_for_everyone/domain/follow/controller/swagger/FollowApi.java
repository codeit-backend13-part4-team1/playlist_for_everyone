package com.codeit.sb13.playlist_for_everyone.domain.follow.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.request.FollowRequest;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;


/**
 * 팔로우 API의 Swagger 계약을 모은 인터페이스.
 * Principal은 서버가 주입하므로 문서 입력에서 숨기고, 실제 주입 어노테이션은 컨트롤러에 둔다.
 *
 * <p>인증·소유자 인가의 HTTP 통합 검증과 Swagger 설명 정리는 #76의 남은 작업이다.
 * 아래 응답 설명의 기존 CSRF 이슈 표기도 후속 정리 대상이며, CSRF 연동·검증의 현재 범위는 #114다.
 */
@Tag(name = "팔로우", description = """
        팔로우 등록·취소 및 조회 API입니다.\n
        모든 팔로우 API는 최종적으로 JWT 인증을 요구합니다.\n
        인증·인가·CSRF 적용 및 실제 보안 응답 검증은 #76 이슈에서 진행합니다.
        """)
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "401",
                description = """
                        인증 정보가 없거나 JWT가 만료·변조된 요청\n
                        (최종 계약, #76 이슈에서 적용·검증)
                        """,
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content = @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        )
})
public interface FollowApi {

    @Operation(
            summary = "팔로우 등록",
            description = """
                    지정한 사용자를 팔로우합니다.\n
                    새로 등록하면 201, 이미 팔로우 중이면 기존 관계를 200으로 반환합니다.\n
                    자기 자신은 팔로우할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "팔로우 신규 등록 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FollowDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "이미 팔로우 중인 기존 관계 반환",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FollowDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                            CSRF 토큰이 없거나 일치하지 않는 요청\n
                            (최종 계약, #76 이슈에서 적용·검증)
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<FollowDto> follow(@Valid @RequestBody FollowRequest request,
                                     @Parameter(hidden = true)
                                     SecurityUser user);

    @Operation(
            summary = "팔로우 취소",
            description = """
                    본인이 등록한 팔로우 관계를 취소합니다.\n
                    이미 취소되었거나 존재하지 않는 관계도 204로 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "팔로우 취소 처리완료",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = """
                            다른 사용자의 팔로우 관계를 취소하려는 요청 또는 CSRF 토큰이 없거나 일치하지 않는 요청\n
                            (최종 계약, #76 이슈에서 적용·검증)
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> unfollow(
            @Parameter(
                    description = "취소할 팔로우 관계 ID",
                    required = true
            )
            @PathVariable UUID followId,
            @Parameter(hidden = true)
            SecurityUser user
    );

    @Operation(
            summary = "내 팔로우 관계 조회",
            description = """
                    본인이 지정한 사용자를 팔로우한 관계를 조회합니다.\n
                    팔로우 관계가 존재하지 않으면 404로 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팔로우 관계 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FollowDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "본인과 대상 사용자 사이의 팔로우 관계가 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<FollowDto> getFollowedByMe(
            @ParameterObject
            @Valid FollowRequest request,
            @Parameter(hidden = true)
            SecurityUser user);

    @Operation(
            summary = "팔로워 수 조회",
            description = """
                    지정한 사용자를 팔로우하는 사용자 수를 조회합니다.\n
                    팔로워가 없으면 0을 반환합니다.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "팔로워 수 조회 성공",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = Long.class)
            )
    )
    ResponseEntity<Long> getFollowCount(
            @ParameterObject
            @Valid FollowRequest request);
}
