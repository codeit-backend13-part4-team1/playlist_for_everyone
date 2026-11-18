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
 * <p>공통 JWT 필터가 검증한 SecurityUser에서 요청자 UUID를 추출하여 서비스에 전달한다.
 * 목록 조회에서는 저장소가 해당 UUID를 수신자 조건으로 사용한다.
 * 읽음 처리에서는 서비스가 알림 ID로 비관적 잠금 조회를 요청하고,
 * 조회한 알림의 수신자와 요청자 UUID가 일치할 때만 삭제한다.
 *
 * <p>정상 처리된 읽음 요청은 204와 빈 본문을 반환한다.
 * 이미 삭제되었거나 존재하지 않는 알림, 타인의 알림에 대한 요청도 같은 응답을 반환하며,
 * 타인의 알림은 삭제하지 않는다.
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
