package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.request.FollowRequest;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    @PostMapping
    public ResponseEntity<FollowDto> follow(@Valid @RequestBody FollowRequest request){
        // 인증 붙이기전에 임시 null적용
        UUID followerId = null;
        return handleFollowResult(followService.follow(request.toFollowCommand(followerId)));
    }

    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> unfollow(@PathVariable UUID followId){
        // 인증 붙이기전에 임시 null적용
        UUID followerId = null;
        followService.unfollow(new UnFollowCommand(followId, followerId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    public void getFollowCount(@Valid FollowRequest request){

    }

    private ResponseEntity<FollowDto> handleFollowResult(FollowResult result) {
        if (result.created()){
            return ResponseEntity.status(HttpStatus.CREATED).body(result.followDto());
        }
        return ResponseEntity.ok(result.followDto());
    }
}
