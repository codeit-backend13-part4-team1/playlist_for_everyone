package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "플레이리스트", description = """
        플레이리스트 생성·조회·수정·삭제, 콘텐츠 추가·제거, 구독·구독 취소 API입니다.\n
        모든 플레이리스트 API는 JWT 인증을 요구합니다.
        """)
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "인증 정보가 없거나 JWT가 만료·변조된 요청",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "서버 내부 오류",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiErrorResponse.class)))
})
@SecurityRequirement(name = "bearerAuth")
public interface PlaylistApi {

  @Operation(
      summary = "플레이리스트 생성",
      description = """
                로그인한 사용자를 소유자로 하는 플레이리스트를 생성합니다.\n
                title은 필수이며 100자 이하, description은 필수이며 255자 이하입니다.
                """
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "플레이리스트 생성 성공",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PlaylistResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "요청 본문 검증 실패(title·description이 비었거나 길이 초과)",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PlaylistResponse> create(
      @Parameter(hidden = true) SecurityUser principal,
      @Valid @RequestBody PlaylistCreateRequest request);


  @Operation(
      summary = "플레이리스트 단건 조회",
      description = """
                플레이리스트 한 건을 조회합니다.\n
                응답의 subscribedByMe는 요청한 사용자의 구독 여부입니다.
                """
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "플레이리스트 조회 성공",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PlaylistResponse.class))),

      @ApiResponse(responseCode = "400", description = "playlistId가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),

      @ApiResponse(responseCode = "404", description = "플레이리스트를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PlaylistResponse> getPlaylist(
      @Parameter(description = "조회할 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트 목록 조회",
      description = """
                플레이리스트 목록을 커서 기반으로 조회합니다.\n
                keywordLike(제목 검색어), ownerIdEqual(소유자), subscriberIdEqual(구독자)로 필터링할 수 있습니다.\n
                sortBy는 updatedAt(수정 시각) 또는 subscribeCount(구독자 수), sortDirection은 ASCENDING 또는 DESCENDING입니다.\n
                limit은 1~100이며 sortBy·sortDirection·limit은 필수입니다.\n
                다음 페이지는 응답의 nextCursor와 nextIdAfter를 cursor와 idAfter로 함께 전달해서 조회합니다.\n
                cursor 형식은 sortBy가 updatedAt이면 ISO 날짜시간, subscribeCount이면 0 이상의 정수입니다.
                """
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "플레이리스트 목록 조회 성공"),
      @ApiResponse(responseCode = "400",
          description = "필수 파라미터 누락, limit 범위 초과, cursor 형식 오류, cursor와 idAfter 중 하나만 전달",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<CursorResponse<PlaylistResponse>> getPlaylists(
      @ParameterObject @Valid PlaylistSearchRequest request,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트 수정",
      description = """
                플레이리스트의 제목과 설명을 수정합니다. 소유자만 수정할 수 있습니다.\n
                title과 description 중 하나 이상을 전달해야 하며, 전달한 값은 공백일 수 없습니다.\n
                전달하지 않은 값은 기존 값을 유지합니다.
                """
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "플레이리스트 수정 성공",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = PlaylistResponse.class))),
      @ApiResponse(responseCode = "400",
          description = "요청 본문 검증 실패(둘 다 미전달, 공백, 길이 초과) 또는 playlistId가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "403", description = "플레이리스트의 소유자가 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "플레이리스트를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PlaylistResponse> updatePlaylist(
      @Parameter(description = "수정할 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트 삭제",
      description = "플레이리스트를 삭제합니다. 소유자만 삭제할 수 있습니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "플레이리스트 삭제 성공", content = @Content),
      @ApiResponse(responseCode = "400", description = "playlistId가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "403", description = "플레이리스트의 소유자가 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "플레이리스트를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> deletePlaylist(
      @Parameter(description = "삭제할 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트에 콘텐츠 추가",
      description = "플레이리스트에 콘텐츠를 담습니다. 소유자만 추가할 수 있으며 이미 담긴 콘텐츠는 다시 담을 수 없습니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "콘텐츠 추가 성공", content = @Content),
      @ApiResponse(responseCode = "400", description = "이미 플레이리스트에 담긴 콘텐츠이거나 ID가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "403", description = "플레이리스트의 소유자가 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "플레이리스트 또는 콘텐츠를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> addContent(
      @Parameter(description = "콘텐츠를 담을 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Parameter(description = "담을 콘텐츠 ID", required = true)
      @PathVariable UUID contentId,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트에서 콘텐츠 제거",
      description = "플레이리스트에 담긴 콘텐츠를 제거합니다. 소유자만 제거할 수 있으며 담기지 않은 콘텐츠는 제거할 수 없습니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "콘텐츠 제거 성공", content = @Content),
      @ApiResponse(responseCode = "400", description = "플레이리스트에 담기지 않은 콘텐츠이거나 ID가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "403", description = "플레이리스트의 소유자가 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "플레이리스트를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> removeContent(
      @Parameter(description = "콘텐츠를 제거할 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Parameter(description = "제거할 콘텐츠 ID", required = true)
      @PathVariable UUID contentId,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트 구독",
      description = "플레이리스트를 구독합니다. 본인의 플레이리스트와 이미 구독한 플레이리스트는 구독할 수 없습니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "구독 성공", content = @Content),
      @ApiResponse(responseCode = "400", description = "본인의 플레이리스트이거나 이미 구독 중이거나 playlistId가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "플레이리스트를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> subscribe(
      @Parameter(description = "구독할 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) SecurityUser principal);


  @Operation(
      summary = "플레이리스트 구독 취소",
      description = "플레이리스트 구독을 취소합니다. 구독하지 않은 플레이리스트는 취소할 수 없습니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "구독 취소 성공", content = @Content),
      @ApiResponse(responseCode = "400", description = "구독하지 않은 플레이리스트이거나 playlistId가 UUID 형식이 아님",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(responseCode = "404", description = "플레이리스트를 찾을 수 없음",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> unsubscribe(
      @Parameter(description = "구독을 취소할 플레이리스트 ID", required = true)
      @PathVariable UUID playlistId,
      @Parameter(hidden = true) SecurityUser principal);

}
