package com.codeit.sb13.playlist_for_everyone.domain.content.controller;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // TODO: JWT 인증 연동 후 관리자 권한 검증 적용
    // @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContentDto> create(
            @Valid @RequestPart("request") ContentCreateRequest request,
            @RequestPart("thumbnail") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentService.create(request, file));
    }

}
