package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
      @ApiResponse(responseCode = "400", description = "요청 본문 검증 실패(title·description이 비었거나 길이 초과)",
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

}
