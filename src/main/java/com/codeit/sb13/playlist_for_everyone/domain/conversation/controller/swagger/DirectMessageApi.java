package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * DM 조회·읽음 처리의 Swagger 요청·응답 계약을 정의한다.
 * 인증 객체 숨김과 보안 요구사항은 문서 설정이며, 실제 검증은 보안 설정과 서비스에서 수행한다.
 */
@Tag(name = "대화방", description = "사용자 간 1:1 대화방 및 DM API")
@SecurityRequirement(name = "bearerAuth")
public interface DirectMessageApi {

    @Operation(
            summary = "DM 메시지 목록 조회",
            description = """
                    로그인한 사용자가 참여한 대화방의 메시지를 커서 기반으로 조회합니다. \n
                    조회로 메시지의 읽음 상태를 변경하지 않으며, GET 요청에는 CSRF 토큰을 요구하지 않습니다. \n
                    \n
                    **정렬과 페이지 이동** \n
                    \n
                    - limit, sortBy, sortDirection은 필수이며 서버 기본값은 없습니다. \n
                    - sortBy는 메시지 생성 시각인 createdAt만 허용합니다. \n
                    - 생성 시각이 같으면 메시지 ID를 같은 방향으로 정렬합니다. \n
                    - 첫 페이지에서는 cursor와 idAfter를 모두 생략합니다. \n
                    - 다음 페이지에서는 이전 응답의 nextCursor와 nextIdAfter를 각각 cursor와 idAfter로 함께 전달합니다. \n
                    - cursor는 시간대 정보가 없는 ISO LocalDateTime 문자열이고 idAfter는 UUID입니다. 반환받은 값을 그대로 사용합니다. \n
                    \n
                    대화방이 없거나 요청자가 참여하지 않은 경우 모두 404로 응답합니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "cursor",
                            in = ParameterIn.QUERY,
                            description = "이전 응답의 nextCursor. 시간대 정보가 없는 ISO LocalDateTime 문자열이며 idAfter와 함께 전달합니다.",
                            example = "2026-11-15T10:00:00.123456",
                            schema = @Schema(implementation = String.class)
                    ),
                    @Parameter(
                            name = "idAfter",
                            in = ParameterIn.QUERY,
                            description = "이전 응답의 nextIdAfter. 기준 메시지의 UUID이며 cursor와 함께 전달합니다.",
                            example = "770e8400-e29b-41d4-a716-446655440000",
                            schema = @Schema(implementation = UUID.class)
                    ),
                    @Parameter(
                            name = "limit",
                            in = ParameterIn.QUERY,
                            description = "페이지당 조회할 메시지 수. 1 이상의 정수이며 필수입니다.",
                            required = true,
                            example = "20"
                    ),
                    @Parameter(
                            name = "sortBy",
                            in = ParameterIn.QUERY,
                            description = "정렬 기준. 메시지 생성 시각인 createdAt만 허용합니다.",
                            required = true,
                            example = "createdAt",
                            schema = @Schema(implementation = String.class, allowableValues = {"createdAt"})
                    ),
                    @Parameter(
                            name = "sortDirection",
                            in = ParameterIn.QUERY,
                            description = "정렬 방향. 생성 시각과 메시지 ID에 같은 방향을 적용합니다.",
                            required = true,
                            example = "DESCENDING",
                            schema = @Schema(
                                    implementation = String.class,
                                    allowableValues = {"ASCENDING", "DESCENDING"}
                            )
                    )
            }
    )
    @ApiResponse(
            responseCode = "200",
            description = """
                    메시지 목록과 커서 정보를 반환합니다. \n
                    \n
                    - data의 각 항목은 MessageDto이며 sender와 receiver는 실제 발신자·수신자 정보입니다. \n
                    - totalCount는 커서 위치와 관계없이 조회 대상 대화방의 메시지 전체 개수입니다. \n
                    - 다음 페이지가 있으면 hasNext가 true이고, 마지막 반환 메시지의 생성 시각과 ID를 nextCursor와 nextIdAfter로 제공합니다. \n
                    - 마지막 페이지에서는 hasNext가 false이고 nextCursor와 nextIdAfter가 null입니다. \n
                    - 조회 결과가 없으면 data는 빈 배열이고 totalCount는 해당 대화방의 전체 메시지 개수입니다. \n
                    - sortBy와 sortDirection은 요청에 적용한 정렬 기준과 방향입니다.
                    """,
            useReturnTypeSchema = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(
                            name = "마지막 페이지",
                            value = """
                                    {
                                      "data": [
                                        {
                                          "id": "770e8400-e29b-41d4-a716-446655440000",
                                          "conversationId": "550e8400-e29b-41d4-a716-446655440000",
                                          "createdAt": "2026-11-15T10:00:00.123456",
                                          "sender": {
                                            "userId": "110e8400-e29b-41d4-a716-446655440000",
                                            "name": "보낸 사용자",
                                            "profileImageUrl": "https://example.com/profiles/sender.png"
                                          },
                                          "receiver": {
                                            "userId": "220e8400-e29b-41d4-a716-446655440000",
                                            "name": "받는 사용자",
                                            "profileImageUrl": null
                                          },
                                          "content": "같이 볼까요?"
                                        }
                                      ],
                                      "nextCursor": null,
                                      "nextIdAfter": null,
                                      "hasNext": false,
                                      "totalCount": 1,
                                      "sortBy": "createdAt",
                                      "sortDirection": "DESCENDING"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = """
                    다음 경우 잘못된 요청으로 처리합니다. \n
                    \n
                    - conversationId 또는 idAfter가 올바른 UUID 형식이 아닌 경우 \n
                    - limit, sortBy, sortDirection 중 필수 값이 누락된 경우 \n
                    - limit이 정수가 아니거나 1보다 작은 경우 \n
                    - sortBy가 createdAt이 아니거나 sortDirection이 ASCENDING 또는 DESCENDING이 아닌 경우 \n
                    - cursor가 빈 문자열이거나 올바른 ISO LocalDateTime 형식이 아닌 경우 \n
                    - cursor와 idAfter 중 하나만 전달한 경우
                    """,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Bearer Access Token이 없거나 유효하지 않거나, 로그인 세션·사용자 상태 검증에 실패한 경우입니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "대화방이 없거나 요청자가 참여하지 않은 경우입니다. 모두 대화방 미존재 오류를 반환합니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "메시지 조회 또는 프로필 이미지 URL 생성 등 응답 변환 중 서버 오류가 발생한 경우입니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    ResponseEntity<CursorResponse<MessageDto>> list(
            @Parameter(description = "조회할 대화방의 UUID", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID conversationId,
            @ParameterObject @Valid @ModelAttribute
            DirectMessageSearchRequest request,
            @Parameter(hidden = true)
            SecurityUser user
    );

    @Operation(
            summary = "DM 메시지 읽음 처리",
            description = """
                    기준 메시지까지 요청자가 받은 미읽음 메시지를 누적하여 읽음 처리합니다. \n
                    요청 본문은 없으며, 성공하면 200 빈 본문으로 응답합니다. \n
                    \n
                    **읽음 처리 범위** \n
                    \n
                    - 요청한 대화방에서 기준 메시지보다 먼저 생성된 메시지와 기준 메시지를 포함합니다. \n
                    - 생성 시각이 같으면 ID가 기준 메시지의 ID 이하인 메시지를 포함합니다. \n
                    - 그중 요청자가 수신한 미읽음 메시지만 변경하며, 자신이 보낸 메시지도 읽음 처리의 기준으로 사용할 수 있습니다. \n
                    - 반복하거나 이전 기준으로 요청해도 이미 읽은 메시지의 상태와 수정 시각은 유지합니다. \n
                    - 대화방 미존재·비참여, 기준 메시지 미존재 또는 다른 대화방 소속이면 404로 응답합니다. \n
                    \n
                    **인증과 CSRF** \n
                    \n
                    - Bearer Access Token과 CSRF 토큰이 필요합니다. \n
                    - 먼저 GET /api/auth/csrf-token을 호출하여 XSRF-TOKEN 쿠키를 발급받습니다. \n
                    - 읽음 처리 요청에는 쿠키와 같은 토큰 값을 X-XSRF-TOKEN 헤더에 전달합니다. \n
                    - Swagger UI는 기존 CSRF 설정으로 쿠키의 토큰을 헤더에 전달합니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "읽음 처리가 완료되었습니다. 응답 본문은 없습니다.", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "conversationId 또는 directMessageId가 올바른 UUID 형식이 아닌 경우입니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Bearer Access Token이 없거나 유효하지 않거나, 로그인 세션·사용자 상태 검증에 실패한 경우입니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "CSRF 토큰이 없거나 쿠키와 헤더의 토큰이 일치하지 않는 경우입니다. JWT가 유효해도 CSRF 검증에 실패하면 거절됩니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = """
                    대화방이 없거나 요청자가 참여하지 않으면 ConversationNotFoundException을 반환합니다.
                    기준 메시지가 없거나 요청한 대화방에 속하지 않으면 DirectMessageNotFoundException을 반환합니다.
                    다른 대화방의 참여 여부와 관계없이 같은 404로 처리하며, 메시지의 읽음 상태를 변경하지 않습니다.
                    """,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "메시지 검증 또는 읽음 처리 중 서버 오류가 발생한 경우입니다.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    ResponseEntity<Void> read(
            @Parameter(description = "읽음 처리할 대화방의 UUID", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID conversationId,
            @Parameter(description = "누적 읽음 처리의 기준 메시지 UUID. 자신이 보낸 메시지도 기준으로 사용할 수 있습니다.",
                    required = true, example = "770e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID directMessageId,
            @Parameter(hidden = true)
            SecurityUser user
    );
}
