package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentThumbnailData;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileEmptyException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionDisconnectEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private final ContentReader contentReader;
    private final ContentWriter contentWriter;
    private final ContentMapper contentMapper;
    private final FileService fileService;

    public ContentDto create(ContentCreateRequest request, MultipartFile file) {
        return resolveThumbnailUrl(contentWriter.create(request, file));
    }

    public ContentDto getById(UUID contentId) {
        return resolveThumbnailUrl(contentReader.getById(contentId));
    }

    public ContentDto update(
            UUID contentId,
            ContentUpdateRequest request,
            MultipartFile file
    ) {
        return resolveThumbnailUrl(
                contentWriter.update(contentId, request, file)
        );
    }

    public void deleteById(UUID contentId) {
        contentWriter.deleteById(contentId);

        // TODO: 콘텐츠 삭제 성공 후 WatchingSessionDisconnectEvent 발행 연동
    }

    private ContentDto resolveThumbnailUrl(ContentResult result) {
        String thumbnailUrl = result.fileKey() != null
                ? fileService.getFileUrlByKey(result.fileKey())
                : result.dto().thumbnailUrl();

        return contentMapper.withThumbnailUrl(result.dto(), thumbnailUrl);
    }

}
