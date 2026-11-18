package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.swagger.DirectMessageApi;
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

/**
 * DM 조회·읽음 요청을 서비스 입력으로 변환하는 HTTP 진입점이다.
 *
 * <p>요청자 ID는 클라이언트 입력이 아닌 인증된 SecurityUser에서 가져온다.
 * 요청 바인딩·Bean Validation은 컨트롤러에서, 대화방 참여·기준 메시지 검증은 서비스 흐름에서 처리한다.
 * JWT와 읽음 POST의 CSRF 검증은 공통 보안 설정이 담당하며, 상세 API 계약은 DirectMessageApi에 둔다.
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController implements DirectMessageApi {
    private final DirectMessageService directMessageService;

    /** 검증된 조회 조건에 대화방·요청자 ID를 결합한다. 목록 조회로 읽음 상태를 변경하지 않는다. */
    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<MessageDto>> list(
            @PathVariable UUID conversationId,
            @Valid @ModelAttribute DirectMessageSearchRequest request,
            @AuthenticationPrincipal SecurityUser user){
        return ResponseEntity.ok(directMessageService.search(request.toCondition(conversationId, user.getUserId())));
    }

    /** 경로의 메시지를 누적 읽음 기준으로 전달하고, 처리할 대상이 없는 경우도 200 빈 본문으로 응답한다. */
    @Override
    @PostMapping("/{directMessageId}/read")
    public ResponseEntity<Void> read(
            @PathVariable UUID conversationId,
            @PathVariable UUID directMessageId,
            @AuthenticationPrincipal SecurityUser user){
       directMessageService.read(new DirectMessageReadCommand(conversationId, directMessageId, user.getUserId()));

        return ResponseEntity.ok().build();
    }
}
