package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations/{conversationId}/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {
    private final DirectMessageService directMessageService;

    @GetMapping
    public ResponseEntity<CursorResponse<MessageDto>> list(
            @PathVariable UUID conversationId,
            @Valid @ModelAttribute DirectMessageSearchRequest request,
            @AuthenticationPrincipal SecurityUser user){
        return ResponseEntity.ok(directMessageService.search(request.toCondition(conversationId, user.getUserId())));
    }

    @PostMapping("/{directMessageId}/read")
    public ResponseEntity<Void> read(
            @PathVariable UUID conversationId,
            @PathVariable UUID directMessageId,
            @AuthenticationPrincipal SecurityUser user){
       directMessageService.read(new DirectMessageReadCommand(conversationId, directMessageId, user.getUserId()));

        return ResponseEntity.ok().build();
    }
}
