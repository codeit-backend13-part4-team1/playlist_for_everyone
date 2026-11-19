package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private final ContentReader contentReader;
    private final ContentWriter contentWriter;
    private final ViewingSessionService viewingSessionService;
    private final ContentMapper contentMapper;
    private final FileService fileService;

    public ContentDto create(ContentCreateRequest request, MultipartFile file) {
        return toContentDto(contentWriter.create(request, file), 0L);
    }

    public ContentDto getById(UUID contentId) {
        ContentResult<ContentDto> result = contentReader.getById(contentId);
        long watcherCount = viewingSessionService.getCurrentViewerCount(contentId);

        return toContentDto(result, watcherCount);
    }

    /** 플레이리스트 일괄 조회 */
    public List<ContentSummary> getSummariesByIds(List<UUID> contentIds) {
        List<ContentResult<ContentSummary>> results = contentReader.getSummariesByIds(contentIds);

        return results.stream()
                .map(result -> {
                    ContentSummary summary = result.dto();

                    String thumbnailUrl = result.fileKey() != null
                            ? fileService.getFileUrlByKey(result.fileKey())
                            : summary.thumbnailUrl();

                    return contentMapper.toSummary(summary, thumbnailUrl);
                })
                .toList();
    }

    public ContentDto update(
            UUID contentId,
            ContentUpdateRequest request,
            MultipartFile file
    ) {
        ContentResult<ContentDto> result = contentWriter.update(contentId, request, file);
        long watcherCount = viewingSessionService.getCurrentViewerCount(contentId);

        return toContentDto(result, watcherCount);
    }

    public void deleteById(UUID contentId) {
        contentWriter.deleteById(contentId);

        // TODO: 콘텐츠 삭제 성공 후 WatchingSessionDisconnectEvent 발행 연동
    }

    private ContentDto toContentDto(ContentResult<ContentDto> result, Long watcherCount) {
        String thumbnailUrl = result.fileKey() != null
                ? fileService.getFileUrlByKey(result.fileKey())
                : result.dto().thumbnailUrl();

        return contentMapper.toContentDto(
                result.dto(),
                thumbnailUrl,
                watcherCount
        );
    }

}
