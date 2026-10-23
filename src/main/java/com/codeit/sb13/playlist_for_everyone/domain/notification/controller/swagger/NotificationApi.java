package com.codeit.sb13.playlist_for_everyone.domain.notification.controller.swagger;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.request.NotificationSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Tag(name = "알림", description = "알림 목록 조회 및 읽음 처리")
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
                responseCode = "500",
                description = "서버 내부 오류",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class)
                )
        )
})
public interface NotificationApi {
    @Operation(
            summary = "알림 목록 조회",
            description = """
                    로그인한 사용자가 수신한 알림을 커서 기반으로 조회합니다.\n
                    생성 시각과 ID를 동일한 방향으로 정렬합니다.\n
                    totalCount는 커서 위치와 무관한 본인의 전체 알림 수입니다.\n
                    마지막 페이지에서는 hasNext가 false이며,\n
                    nextCursor와 nextIdAfter는 null입니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "sortBy",
                            in = ParameterIn.QUERY,
                            description = "정렬 기준",
                            required = true,
                            example = "createdAt",
                            schema = @Schema(
                                    implementation = String.class,
                                    allowableValues = {"createdAt"}
                            )
                    )
            }
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 목록 조회 성공",
            // useReturnTypeSchema = true: CursorResponse<NotificationDto> 반환형으로 성공 응답 스키마를 생성
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json")
    )
    ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
            @ParameterObject @Valid NotificationSearchRequest request
    );

    @Operation(
            summary = "알림 읽음 처리",
            description = """
                    본인이 수신한 알림을 삭제하여 읽음 처리합니다.\n
                    이미 삭제되었거나 존재하지 않는 알림도 204로 반환합니다.\n
                    타인의 알림은 삭제하지 않고 동일하게 204를 반환합니다.
                    """
    )
    @ApiResponse(
            responseCode = "204",
            description = "처리 완료",
            content = @Content
    )
    ResponseEntity<Void> deleteNotification(
            @Parameter(
                    description = "읽음 처리할 알림 ID",
                    required = true
            )
            @PathVariable UUID notificationId);
}
