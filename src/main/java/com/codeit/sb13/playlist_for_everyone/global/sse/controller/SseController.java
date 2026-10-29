package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final SseConnectionService sseConnectionService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void connect(@RequestParam(value = "lastEventId", required = false) UUID lastEventId){

    }
}
