package com.codeit.sb13.playlist_for_everyone.domain.review.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewListRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Tag(name = "리뷰", description = "리뷰 관리")
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
public interface ReviewApi {

    @Operation(
            summary = "리뷰 생성",
            description = """
                    콘텐츠에 대한 리뷰를 생성합니다.
                    로그인한 사용자만 리뷰를 작성할 수 있으며,
                    하나의 콘텐츠에는 사용자당 하나의 리뷰만 작성할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "리뷰 생성 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 해당 콘텐츠에 리뷰를 작성한 경우"
            )
    })
    ResponseEntity<ReviewDto> create(
            @Valid @RequestBody ReviewCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser author
    );

    @Operation(
            summary = "리뷰 목록 조회",
            description = """
                    특정 콘텐츠의 리뷰 목록을 커서 기반 페이지네이션으로 조회합니다.
                    생성일(createdAt) 또는 평점(rating)을 기준으로 정렬할 수 있으며,
                    오름차순(ASCENDING)과 내림차순(DESCENDING)을 지원합니다.
                    다음 페이지가 존재하면 다음 조회에 사용할 커서 정보를 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "리뷰 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")
            )
    })
    ResponseEntity<CursorResponse<ReviewDto>> getAllByContentId(
            @RequestParam(value = "contentId") UUID contentId,
            @Valid @ModelAttribute ReviewListRequest request
    );

    @Operation(
            summary = "리뷰 수정",
            description = """
                기존 리뷰의 내용과 평점을 수정합니다.
                리뷰 작성자만 수정할 수 있으며,
                전달하지 않은 항목은 기존 값을 유지합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "리뷰 수정 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "리뷰를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ReviewDto> update(
            @PathVariable("reviewId") UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser user
    );

    @Operation()
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "리뷰 삭제 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "리뷰를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> delete(
            @PathVariable("reviewId") UUID reviewId,
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityUser author
    );

}
