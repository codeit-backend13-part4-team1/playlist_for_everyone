package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationWithUserRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
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
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * 대화방 생성·조회 API의 Swagger 요청·응답 계약을 컨트롤러 구현과 분리해 정의한다.
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

    @Operation(
            summary = "대화방 목록 조회",
            description = """
                    로그인한 사용자가 참여한 대화방을 커서 기반으로 조회합니다. \n
                    조회만으로 대화방을 생성하거나 메시지를 읽음 처리하지 않습니다. \n
                    \n
                    **검색** \n
                    \n
                    - 상대방 이름 또는 대화방의 전체 메시지 본문을 검색합니다. \n
                    - 검색어의 앞뒤 공백을 제거하며, 빈 검색어는 검색 조건 없이 처리합니다. \n
                    - 대소문자를 구분하지 않는 부분 검색이며, %와 _는 입력한 문자 그대로 검색합니다. \n
                    - 여러 메시지가 일치해도 대화방은 한 번만 반환합니다. \n
                    - 검색된 메시지와 관계없이 응답에는 실제 최신 메시지를 담습니다. \n
                    \n
                    **정렬과 페이지 이동** \n
                    \n
                    - limit, sortBy, sortDirection은 필수이며 서버 기본값은 없습니다. \n
                    - sortBy는 대화방 생성 시각인 createdAt만 허용합니다. \n
                    - 생성 시각이 같으면 대화방 ID를 같은 방향으로 정렬합니다. \n
                    - 첫 페이지에서는 cursor와 idAfter를 모두 생략합니다. \n
                    - 다음 페이지에서는 이전 응답의 nextCursor와 nextIdAfter를 각각 cursor와 idAfter로 함께 전달합니다. \n
                    - cursor는 ISO LocalDateTime 문자열, idAfter는 UUID이며 반환받은 값을 그대로 사용합니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "keywordLike",
                            in = ParameterIn.QUERY,
                            description = "상대방 이름 또는 전체 메시지 본문에 포함된 검색어. 생략하거나 공백만 입력하면 검색 조건 없이 조회합니다.",
                            example = "영화",
                            schema = @Schema(implementation = String.class)
                    ),
                    @Parameter(
                            name = "cursor",
                            in = ParameterIn.QUERY,
                            description = "이전 응답의 nextCursor. 시간대 정보가 없는 ISO LocalDateTime 문자열이며 idAfter와 함께 전달합니다.",
                            example = "2026-11-13T10:30:00.123456",
                            schema = @Schema(implementation = String.class)
                    ),
                    @Parameter(
                            name = "idAfter",
                            in = ParameterIn.QUERY,
                            description = "이전 응답의 nextIdAfter. 커서 기준 대화방의 UUID이며 cursor와 함께 전달합니다.",
                            example = "550e8400-e29b-41d4-a716-446655440000",
                            schema = @Schema(implementation = UUID.class)
                    ),
                    @Parameter(
                            name = "limit",
                            in = ParameterIn.QUERY,
                            description = "페이지당 조회할 대화방 수. 1 이상의 정수이며 필수입니다.",
                            required = true,
                            example = "20"
                    ),
                    @Parameter(
                            name = "sortBy",
                            in = ParameterIn.QUERY,
                            description = "정렬 기준. 대화방 생성 시각인 createdAt만 허용합니다.",
                            required = true,
                            example = "createdAt",
                            schema = @Schema(
                                    implementation = String.class,
                                    allowableValues = {"createdAt"}
                            )
                    ),
                    @Parameter(
                            name = "sortDirection",
                            in = ParameterIn.QUERY,
                            description = "정렬 방향. 생성 시각과 대화방 ID에 같은 방향을 적용합니다.",
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
                    본인이 참여한 대화방의 목록과 커서 정보를 반환합니다. \n
                    \n
                    - data의 각 항목은 ConversationDto이며, with는 요청자 기준 상대방 정보입니다. \n
                    - lastestMessage는 생성 시각 내림차순, 동일 시각에서는 ID 내림차순으로 선택한 실제 최신 메시지입니다. \n
                    - hasUnread는 요청자가 수신한 읽지 않은 메시지가 하나라도 있는지 나타냅니다. \n
                    - 메시지가 없는 대화방은 lastestMessage가 null이고 hasUnread가 false입니다. \n
                    - totalCount는 커서 위치와 관계없이 본인 참여·검색 조건을 만족하는 전체 대화방 수입니다. \n
                    - 다음 페이지가 있으면 hasNext가 true이며, 마지막으로 반환한 항목의 nextCursor와 nextIdAfter를 제공합니다. \n
                    - 마지막 페이지에서는 hasNext가 false이고 nextCursor와 nextIdAfter가 null입니다. \n
                    - 조회 결과가 없으면 data는 빈 배열입니다. \n
                    - sortBy와 sortDirection은 요청에 적용한 정렬 기준과 방향입니다.
                    """,
            useReturnTypeSchema = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
    )
    @ApiResponse(
            responseCode = "400",
            description = """
                    다음 경우 잘못된 요청으로 처리합니다. \n
                    \n
                    - limit, sortBy, sortDirection 중 필수 값이 누락된 경우 \n
                    - limit이 정수가 아니거나 1보다 작은 경우 \n
                    - sortBy가 createdAt이 아니거나 sortDirection이 ASCENDING 또는 DESCENDING이 아닌 경우 \n
                    - cursor가 올바른 ISO LocalDateTime 형식이 아니거나 idAfter가 UUID 형식이 아닌 경우 \n
                    - cursor와 idAfter 중 하나만 전달한 경우
                    """,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Bearer Access Token이 없거나 유효하지 않거나, 로그인 세션·사용자 상태 검증에 실패한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "대화방 조회 또는 프로필 이미지 URL 생성 등 응답 변환 중 서버 오류가 발생한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    ResponseEntity<CursorResponse<ConversationDto>> search(
            @Parameter(hidden = true)
            SecurityUser user,
            @ParameterObject @Valid @ModelAttribute
            ConversationSearchRequest request);

    @Operation(
            summary = "대화방 단건 조회",
            description = """
                    로그인한 사용자가 참여한 대화방을 ID로 조회합니다. \n
                    \n
                    대화방이 없거나 요청자가 참여하지 않은 경우 모두 404로 응답합니다. \n
                    조회만으로 대화방을 생성하거나 메시지를 읽음 처리하지 않습니다.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = """
                    조회한 대화방 정보를 반환합니다. \n
                    \n
                    - with는 요청자 기준 상대방 정보입니다. \n
                    - lastestMessage는 생성 시각 내림차순, 동일 시각에서는 ID 내림차순으로 선택한 실제 최신 메시지입니다. \n
                    - hasUnread는 요청자가 수신한 읽지 않은 메시지가 하나라도 있는지 나타냅니다. \n
                    - 메시지가 없으면 lastestMessage는 null, hasUnread는 false입니다.
                    """,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ConversationDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "conversationId가 올바른 UUID 형식이 아닌 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Bearer Access Token이 없거나 유효하지 않거나, 로그인 세션·사용자 상태 검증에 실패한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "대화방이 없거나 요청자가 해당 대화방의 참여자가 아닌 경우입니다. 두 경우 모두 같은 대화방 미존재 오류를 반환합니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "대화방 조회 또는 프로필 이미지 URL 생성 등 응답 변환 중 서버 오류가 발생한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    ResponseEntity<ConversationDto> getConversation(
            @Parameter(hidden = true)
            SecurityUser user,
            @Parameter(
                    description = "조회할 대화방의 UUID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable
            UUID conversationId);

    @Operation(
            summary = "상대방과의 대화방 조회",
            description = """
                    로그인한 사용자와 지정한 상대방 사이의 기존 대화방을 조회합니다. \n
                    \n
                    - 두 참여자 중 누가 요청하더라도 같은 대화방을 조회하며, with는 요청자 기준 상대방 정보입니다. \n
                    - 자기 자신을 상대방으로 지정하면 400으로 응답합니다. \n
                    - 상대 사용자가 없거나 두 사용자 사이의 대화방이 없으면 404로 응답합니다. \n
                    - 조회만으로 대화방을 생성하거나 메시지를 읽음 처리하지 않습니다.
                    """,
            parameters = @Parameter(
                    name = "userId",
                    in = ParameterIn.QUERY,
                    description = "대화 상대 사용자의 UUID. 요청자 ID는 인증 정보에서 가져옵니다.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(implementation = UUID.class)
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = """
                    두 사용자 사이의 기존 대화방 정보를 반환합니다. \n
                    \n
                    - with는 요청자 기준 상대방 정보입니다. \n
                    - lastestMessage는 생성 시각 내림차순, 동일 시각에서는 ID 내림차순으로 선택한 실제 최신 메시지입니다. \n
                    - hasUnread는 요청자가 수신한 읽지 않은 메시지가 하나라도 있는지 나타냅니다. \n
                    - 메시지가 없으면 lastestMessage는 null, hasUnread는 false입니다.
                    """,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ConversationDto.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "userId가 누락되거나 UUID 형식이 아닌 경우, 또는 자기 자신의 ID를 지정한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Bearer Access Token이 없거나 유효하지 않거나, 로그인 세션·사용자 상태 검증에 실패한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "상대 사용자가 없거나 두 사용자 사이의 대화방이 없는 경우입니다. 모두 대화방 미존재 오류를 반환합니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "대화방 조회 또는 프로필 이미지 URL 생성 등 응답 변환 중 서버 오류가 발생한 경우입니다.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    ResponseEntity<ConversationDto> getWith(
            @Parameter(hidden = true)
            SecurityUser user,
            @ParameterObject @Valid @ModelAttribute
            ConversationWithUserRequest request);
}
