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
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileEmptyException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContentWriter {

    private final ContentRepository contentRepository;
    private final WatchingSessionService watchingSessionService;
    private final ContentMapper contentMapper;
    private final FileService fileService;

    @Transactional
    public ContentResult create(
            ContentCreateRequest request,
            MultipartFile file
    ) {
        // 관리자 콘텐츠는 썸네일 파일 없이는 등록이 불가합니다.
        if (file == null || file.isEmpty()) {
            throw new FileEmptyException(Map.of());
        }

        FileInfo fileInfo = fileService.upload(file);

        Content content = Content.createAdminContent(
                request.type(),
                request.title(),
                request.description(),
                null,
                fileInfo
        );

        normalizeTags(request.tags()).forEach(content::addTag);

        Content savedContent = contentRepository.save(content);

        ContentThumbnailData thumbnailData = getThumbnailData(savedContent);

        ContentDto dto = contentMapper.toDto(
                savedContent,
                thumbnailData.thumbnailUrl(),
                0.0, 0, 0L
        );

        return new ContentResult(dto, thumbnailData.fileKey());
    }

    @Transactional
    public ContentResult update(
            UUID contentId,
            ContentUpdateRequest request,
            MultipartFile file
    ) {
        ContentDetailProjection detail = contentRepository.findDetailById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(
                        Map.of("contentId", contentId)
                ));

        Content content = detail.getContent();

        // 제목, 설명, 태그 수정
        if (request != null) {
            List<String> tags = request.tags() == null
                    ? null
                    : normalizeTags(request.tags());

            content.update(
                    request.title(),
                    request.description(),
                    tags
            );
        }

        // 썸네일 파일 교체
        if (file != null && !file.isEmpty()) {
            FileInfo oldFileInfo = content.getFileInfo();

            FileInfo newFileInfo = fileService.upload(file);
            content.updateThumbnail(null, newFileInfo);

            contentRepository.flush();

            if (oldFileInfo != null) {
                fileService.delete(oldFileInfo.getId());
            }
        }

        ContentThumbnailData thumbnailData = getThumbnailData(content);
        long watcherCount = watchingSessionService.getCurrentViewerCount(contentId);

        ContentDto contentDto = contentMapper.toDto(
                content,
                thumbnailData.thumbnailUrl(),
                detail.getAverageRating(),
                Math.toIntExact(detail.getReviewCount()),
                watcherCount
        );

        return new ContentResult(
                contentDto,
                thumbnailData.fileKey()
        );
    }

    // 썸네일 URL 및 S3 파일 Key 추출
    private ContentThumbnailData getThumbnailData(Content content) {
        FileInfo fileInfo = content.getFileInfo();

        return new ContentThumbnailData(
                content.getThumbnailUrl(),
                fileInfo != null ? fileInfo.getKey() : null
        );
    }

    // 태그 중복 검사
    // 태그가 없는 경우 빈 리스트로 처리
    // 중간 공백을 제외한 문자열이 같으면 먼저 입력된 태그만 저장
    private List<String> normalizeTags(List<String> tags) {
        Set<String> seen = new HashSet<>();

        return Optional.ofNullable(tags)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(tag -> !tag.isBlank())
                .filter(tag -> seen.add(tag.replaceAll("(?U)\\s+", "")))
                .toList();
    }

}
