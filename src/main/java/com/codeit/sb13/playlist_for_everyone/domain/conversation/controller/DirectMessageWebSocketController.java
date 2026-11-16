package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSendRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

    private final DirectMessageService directMessageService;

    public void send(UUID conversationId, DirectMessageSendRequest request, Authentication authentication){

    }
}
