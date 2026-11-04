package com.codeit.sb13.playlist_for_everyone.domain.notification.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.notification.controller.swagger.NotificationApi;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.request.NotificationSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 인증된 사용자의 알림 목록 조회와 읽음 처리를 제공한다.
 *
 * <p>공통 JWT 필터가 검증한 SecurityUser에서 요청자 UUID를 추출한다.
 * 이 UUID를 조회 조건과 삭제 서비스에 전달하고, 저장소가 수신자 조건으로 사용한다.
 * 클라이언트가 수신자 ID를 선택하거나 서비스가 인증 컨텍스트를 다시 조회하지 않는다.
 *
 * <p>읽음 처리는 본인이 수신한 알림을 삭제하는 방식이다. 이미 삭제되었거나 존재하지 않는
 * 알림, 타인의 알림에 대한 요청도 204를 반환하며, 타인의 알림은 삭제하지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController implements NotificationApi {

    private final NotificationService service;

    @Override
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
            @Valid NotificationSearchRequest request,
            @AuthenticationPrincipal SecurityUser user){
        return ResponseEntity.ok(service.getNotifications(request.toCondition(user.getUserId())));
    }

    @Override
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal SecurityUser user){
        service.confirm(notificationId, user.getUserId());
        return ResponseEntity.noContent().build();
    }
}
