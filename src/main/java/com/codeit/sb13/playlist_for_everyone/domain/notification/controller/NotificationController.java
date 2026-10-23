package com.codeit.sb13.playlist_for_everyone.domain.notification.controller;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.request.NotificationSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ResponseEntity<CursorResponse<NotificationDto>> getNotifications(@Valid NotificationSearchRequest request) {
        return ResponseEntity.status(501).build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID notificationId) {
        return ResponseEntity.status(501).build();
    }
}
