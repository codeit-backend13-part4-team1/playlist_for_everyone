package com.codeit.sb13.playlist_for_everyone.domain.content.controller;

import com.codeit.sb13.playlist_for_everyone.domain.content.controller.swagger.ContentApi;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.CursorResponseWatchingSessionDto;
import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.request.WatchingSessionRequestByContent;
import com.codeit.sb13.playlist_for_everyone.domain.watching.service.WatchingSessionHttpService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController implements ContentApi {

    private final ContentService contentService;
    private final ContentSearchService contentSearchService;
    private final WatchingSessionHttpService watchingSessionService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentDto> create(
            @Valid @RequestPart("request") ContentCreateRequest request,
            @RequestPart("thumbnail") MultipartFile file
    ) {
        ContentDto response = contentService.create(request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<ContentDto>> search(
            @Valid @ModelAttribute ContentSearchRequest request
    ) {
        return ResponseEntity.ok(contentSearchService.search(request.toCondition()));
    }

    @Override
    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDto> get(@PathVariable UUID contentId) {
        return ResponseEntity.ok(contentService.getById(contentId));
    }

    @Override
    @PatchMapping(
            value = "/{contentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ContentDto> update(
            @PathVariable UUID contentId,
            @Valid @RequestPart("request") ContentUpdateRequest request,
            @RequestPart(value="thumbnail", required=false) MultipartFile file
    ) {
        return ResponseEntity.ok(contentService.update(contentId, request, file));
    }

    @Override
    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID contentId) {
        contentService.deleteById(contentId);

        return ResponseEntity.noContent().build();
    }
}
