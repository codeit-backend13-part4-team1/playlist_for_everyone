package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.swagger.ConversationApi;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.ConversationWithUserRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 대화방 생성·목록·단건·상대방 기준 조회 요청을 서비스 입력으로 변환하는 HTTP 진입점이다.
 *
 * <p>요청자 ID는 인증된 SecurityUser에서 가져와 생성 Command 또는 조회 조건에 담는다.
 * 생성 요청 본문과 조회 쿼리 파라미터는 Bean Validation으로 검증하며,
 * 대화방 참여자 제한과 미존재 처리는 서비스·조회 계층에 맡긴다.
 *
 * <p>JWT 인증과 CSRF 검증은 공통 보안 설정에서 처리한다.
 * 생성 POST 요청에는 CSRF 토큰이 필요하며, 조회 GET 요청에는 요구하지 않는다.
 *
 * <p>생성과 조회 성공 시 모두 HTTP 200으로 응답한다. 목록은 CursorResponse로,
 * 생성·단건·상대방 기준 조회는 ConversationDto로 반환한다.
 * 조회 요청은 대화방을 생성하거나 메시지를 읽음 처리하지 않는다.
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

    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<ConversationDto>> search(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @ModelAttribute ConversationSearchRequest request){
        return ResponseEntity.ok(conversationService.search(
                request.toCondition(user.getUserId())));
    }

    @Override
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDto> getConversation(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable UUID conversationId){
        return ResponseEntity.ok(conversationService.findById(
                new ConversationFindByIdCondition(user.getUserId(), conversationId)));
    }

    @Override
    @GetMapping("/with")
    public ResponseEntity<ConversationDto> getWith(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @ModelAttribute ConversationWithUserRequest request){
        return ResponseEntity.ok(conversationService.findWithUser(
                request.toConversationFindCondition(user.getUserId())));
    }
}
