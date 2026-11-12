package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 대화방 생성 API의 Swagger 요청·응답 계약을 컨트롤러 구현과 분리해 정의한다.
 * 인증 파라미터 숨김과 보안 요구사항 표시는 문서 설정이며, 실제 인증·인가 검증은 보안 설정이 담당한다.
 */
@Tag(name = "대화방",description = "사용자 간 1:1 대화방 API")
@SecurityRequirement(name = "bearerAuth")
public interface ConversationApi {

    @Operation(
            summary = "대화방 생성",
            description = """
                    로그인한 사용자와 지정한 상대방 사이의 대화방을 생성합니다. \n
                    두 사용자 사이에 대화방이 있으면 기존 대화방을 반환합니다. \n
                    신규 생성과 기존 대화방 반환 모두 200으로 응답합니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = """
                            withUserId는 대화 상대 사용자의 UUID이며 필수 값입니다. \n
                            요청자 ID는 인증 정보에서 가져옵니다.
                            """,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ConversationCreateRequest.class),
                            examples = @ExampleObject(
                                    name = "대화방 생성 요청",
                                    value = """
                                            {
                                                "withUserId": "550e8400-e29b-41d4-a716-446655440000"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = """
                    신규 대화방을 생성하거나 기존 대화방을 반환합니다. \n
                    with는 요청자를 기준으로 한 상대방 정보입니다. \n
                    신규 대화방은 lastestMessage가 null이고 hasUnread는 false입니다. \n
                    기존 대화방은 실제 최신 메시지와 요청자의 미읽음 여부를 반환합니다. \n
                    메시지가 없는 대화방은 lastestMessage가 null입니다.
                    """,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ConversationDto.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = """
                    다음 경우 잘못된 요청으로 처리합니다. \n
                    - withUserId가 누락되거나 null인 경우 \n
                    - withUserId가 올바른 UUID 형식이 아닌 경우 \n
                    - 자기 자신과의 대화방 생성을 요청한 경우 \n
                    - 대화 상대 사용자가 존재하지 않는 경우
                    """,
            content = @Content(
                    mediaType =  MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = """
                    인증 정보가 없거나 유효하지 않은 경우입니다. \n
                    - Bearer Access Token이 없거나 만료·변조된 경우 \n
                    - 로그인 세션이 만료되거나 무효화된 경우 \n
                    - 토큰에 해당 사용자가 없거나 계정이 잠긴 경우
                    """,
            content = @Content(
                    mediaType =  MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = """
                    CSRF 토큰이 없거나 일치하지 않는 경우입니다. \n
                    요청에는 XSRF-TOKEN 쿠키와 해당 토큰 값을 담은 X-XSRF-TOKEN 헤더가 필요합니다. \n
                    JWT 인증 정보가 유효해도 CSRF 검증에 실패하면 요청이 거절됩니다.
                    """,
            content = @Content(
                    mediaType =  MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = """
                    대화방 생성 또는 응답 생성 중 서버오류가 발생한 경우입니다. \n
                    대화방 저장이 완료된 후 프로필 이미지 URL 생성에 실패한 경우에도 오류 응답을 반환합니다. \n
                    이 경우 이미 저장된 대화방은 유지됩니다.
                    """,
            content = @Content(
                    mediaType =  MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    ResponseEntity<ConversationDto> create(
            @Parameter(hidden = true)
            SecurityUser user,
            @Valid @RequestBody ConversationCreateRequest request
    );
}
