package com.codeit.sb13.playlist_for_everyone.global.websocket.controller;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatSendRequest;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.ContentChatSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;

    @MessageMapping("/contents/{contentId}/chat")
    @PreAuthorize("hasRole('USER')")
    public void contentChatReceive(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Authentication authentication,
            ContentChatSendRequest message
    ){

        SecurityUser sUser = (SecurityUser) authentication.getPrincipal();

        // 로직상 NPE 일어나지 않음.
        User user = userService.getById(sUser.getUserId());

        eventPublisher.publishEvent(new ContentChatSendEvent(
                contentId,
                new UserSummary(
                        user.getId(),
                        user.getName(),
                        null    // profile image url
                ),
                message.content()));
    }

}
