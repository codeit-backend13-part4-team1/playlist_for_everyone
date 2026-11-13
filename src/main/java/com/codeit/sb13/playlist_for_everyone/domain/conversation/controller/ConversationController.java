package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.swagger.ConversationApi;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증된 사용자의 대화방 생성 요청을 서비스 입력으로 변환하는 HTTP 진입점이다.
 *
 * <p>인증·CSRF 검증은 공통 보안 설정에서 처리하며, 요청자 ID는 인증된 SecurityUser에서 가져온다.
 * 요청 본문에는 상대방 ID만 받고 Bean Validation으로 필수 값을 검사한다.
 * 신규 생성과 기존 대화방 반환 모두 API 계약에 따라 HTTP 200으로 응답한다.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController implements ConversationApi {

    private final ConversationService conversationService;

    @Override
    @PostMapping
    public ResponseEntity<ConversationDto> create(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody ConversationCreateRequest request){

        return ResponseEntity.ok(conversationService.create(request.toCommand(user.getUserId())));
    }
}
