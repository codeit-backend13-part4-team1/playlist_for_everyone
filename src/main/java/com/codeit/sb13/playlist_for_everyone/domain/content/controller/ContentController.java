package com.codeit.sb13.playlist_for_everyone.domain.content.controller;

import com.codeit.sb13.playlist_for_everyone.domain.content.controller.swagger.ContentApi;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController implements ContentApi {

    private final ContentService contentService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContentDto> create(
            @Valid @RequestPart("request") ContentCreateRequest request,
            @RequestPart("thumbnail") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentService.create(request, file));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(
            value = "/{contentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ContentDto> update(
            @PathVariable UUID contentId,
            @Valid @RequestPart("request") ContentUpdateRequest request,
            @RequestPart(value="thumbnail", required=false) MultipartFile file
    ) {
        return ResponseEntity.ok(
                contentService.update(contentId, request, file)
        );
    }

}
