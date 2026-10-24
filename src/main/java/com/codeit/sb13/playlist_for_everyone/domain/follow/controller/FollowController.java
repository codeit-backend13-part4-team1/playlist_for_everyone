package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.request.FollowRequest;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    @PostMapping
    public ResponseEntity<FollowDto> follow(@RequestBody FollowRequest request){

        return null;
    }

    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> unfollow(@PathVariable UUID followId){

        return null;
    }
}
