package com.codeit.sb13.playlist_for_everyone.domain.content.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "콘텐츠", description = "콘텐츠 관리")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청값",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "401",
                description = "인증되지 않은 요청",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "403",
                description = "접근 권한이 없는 요청",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        )
})
public interface ContentApi {

    @Operation(
            summary = "[어드민] 콘텐츠 생성",
            description = """
                    관리자가 새로운 콘텐츠를 생성합니다.
                    콘텐츠 타입, 제목, 설명, 태그와 썸네일 이미지를 등록합니다.
                    썸네일 이미지는 필수입니다.
                    """
    )
    @ApiResponse(
            responseCode = "201",
            description = "콘텐츠 생성 성공",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json")
    )
    ResponseEntity<ContentDto> create(
            @Valid @RequestPart("request") ContentCreateRequest request,
            @RequestPart("thumbnail") MultipartFile file
    );

    @Operation(
            summary = "[어드민] 콘텐츠 수정",
            description = """
                    관리자가 기존 콘텐츠를 수정합니다.
                    제목, 설명, 태그와 썸네일 이미지를 변경할 수 있습니다.
                    제목, 설명, 태그를 전달하지 않으면 기존 값을 유지합니다.
                    썸네일 이미지는 선택 사항입니다.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "콘텐츠 수정 성공",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json")
    )
    @ApiResponse(
            responseCode = "404",
            description = "콘텐츠를 찾을 수 없음",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)
            )
    )
    ResponseEntity<ContentDto> update(
            @Parameter(
                    description = "수정할 콘텐츠 ID",
                    required = true
            )
            @PathVariable UUID contentId,

            @Valid @RequestPart("request") ContentUpdateRequest request,

            @RequestPart(value = "thumbnail", required = false)
            MultipartFile file
    );



}
