package com.codeit.sb13.playlist_for_everyone.domain.watching.controller;


import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.CursorResponseWatchingSessionDto;
import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.request.WatchingSessionRequestByContent;
import com.codeit.sb13.playlist_for_everyone.domain.watching.service.WatchingSessionHttpService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WatchingSessionHttpController {

    private final WatchingSessionHttpService watchingSessionService;

    @GetMapping("/users/{watcherId}/watching-sessions")
    ResponseEntity<WatchingSessionDto> getWatchingSessionByUser(
            @PathVariable UUID watcherId
    ){
        WatchingSessionDto dto = watchingSessionService.getWatchingSessionByUserId(watcherId);
        return ResponseEntity.ok(dto);
    };

    @GetMapping("/contents/{contentId}/watching-sessions")
    public ResponseEntity<?> getCurrentInfoByContent(
            @PathVariable UUID contentId,
            @RequestParam(value = "watcherNameLike", required = false) String userName,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "idAfter", required = false) UUID idAfter,
            @RequestParam(value = "limit") Integer limit,
            @RequestParam(value = "sortDirection",defaultValue = "ASCENDING") String sortDirection,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy
    ){
        CursorResponseWatchingSessionDto<WatchingSessionDto> response =
                watchingSessionService.getCurrentSessionInfo(
                        contentId,
                        new WatchingSessionRequestByContent(
                                userName,
                                cursor,
                                idAfter,
                                limit,
                                sortDirection,
                                sortBy
                        )
                );

        return ResponseEntity.ok(response);
    }


}
