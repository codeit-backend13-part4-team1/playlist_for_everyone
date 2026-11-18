package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.follow.controller.swagger.FollowApi;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.request.FollowRequest;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 팔로우 HTTP 입력과 서비스 계약을 연결한다.
 *
 * <p>공통 보안 필터에서 인증된 SecurityUser의 ID로 Command를 만든다.
 * 클라이언트는 대상 사용자 ID 또는 관계 ID만 전달하며 요청자 ID를 지정하지 않는다.
 * 입력 형식 검증은 MVC/Bean Validation, 업무 규칙과 취소 소유자 인가는 서비스 계층이 담당한다.
 * 응답별 설명은 FollowApi, 흐름과 검증 상태는 docs/follow 가이드에서 관리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController implements FollowApi {

    private final FollowService followService;

    @Override
    @PostMapping
    public ResponseEntity<FollowDto> follow(@Valid @RequestBody FollowRequest request,
                                            @AuthenticationPrincipal SecurityUser user){
        UUID followerId = user.getUserId();
        return handleFollowResult(followService.follow(request.toFollowCommand(followerId)));
    }

    @Override
    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> unfollow(@PathVariable UUID followId,
                                         @AuthenticationPrincipal SecurityUser user){
        UUID followerId = user.getUserId();
        followService.unfollow(new UnFollowCommand(followId, followerId));
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/followed-by-me")
    public ResponseEntity<FollowDto> getFollowedByMe(@Valid FollowRequest request,
                                                     @AuthenticationPrincipal SecurityUser user){
        UUID followerId = user.getUserId();
        return ResponseEntity.ok(followService.findFollowDto(request.toFollowCommand(followerId)));
    }

    @Override
    @GetMapping("/count")
    public ResponseEntity<Long> getFollowCount(@Valid FollowRequest request){
        // 공통 필터의 인증은 필요하지만, 집계 조건에는 요청자 ID나 관리자 권한이 필요하지 않다.
        return ResponseEntity.ok(followService.countFollowers(request.followeeId()));
    }

    // 신규 생성과 기존 관계 반환을 구분하되 응답 DTO 형식은 동일하게 유지한다.
    private ResponseEntity<FollowDto> handleFollowResult(FollowResult result) {
        if (result.created()){
            return ResponseEntity.status(HttpStatus.CREATED).body(result.followDto());
        }
        return ResponseEntity.ok(result.followDto());
    }
}
