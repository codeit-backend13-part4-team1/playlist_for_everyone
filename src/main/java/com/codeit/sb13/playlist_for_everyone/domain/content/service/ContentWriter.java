package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentThumbnailData;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentSearchCacheEvictEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileEmptyException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentWriter {

    private final ContentRepository contentRepository;
    private final ReviewRepository reviewRepository;
    private final PlaylistContentRepository playlistContentRepository;
    private final ApplicationEventPublisher eventPublisher;
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

        // DB 커밋 이후 검색 캐시를 무효화하도록 이벤트 발행
        eventPublisher.publishEvent(new ContentSearchCacheEvictEvent());

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

        ContentDto contentDto = contentMapper.toDto(
                content,
                thumbnailData.thumbnailUrl(),
                detail.getAverageRating(),
                Math.toIntExact(detail.getReviewCount()),
                0L
        );

        // DB 커밋 이후 검색 캐시를 무효화하도록 이벤트 발행
        eventPublisher.publishEvent(new ContentSearchCacheEvictEvent());

        return new ContentResult(
                contentDto,
                thumbnailData.fileKey()
        );
    }

    @Transactional
    public void deleteById(UUID contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(
                        Map.of("contentId", contentId)
                ));

        int deletedReviews = reviewRepository.deleteAllByContentId(contentId);
        int deletedPlaylistContents = playlistContentRepository.deleteAllByContentId(contentId);

        contentRepository.delete(content);

        // 콘텐츠 삭제를 DB에 반영한 후 파일 정보 삭제
        contentRepository.flush();

        // S3 파일 정보가 있는 경우에만 삭제
        FileInfo fileInfo = content.getFileInfo();
        if (fileInfo != null) {
            fileService.delete(fileInfo.getId());
        }

        // DB 커밋 이후 검색 캐시를 무효화하도록 이벤트 발행
        eventPublisher.publishEvent(new ContentSearchCacheEvictEvent());

        log.debug(
                "콘텐츠 삭제: contentId={}, 삭제된 리뷰={}, 삭제된 플레이리스트 연결={}",
                contentId, deletedReviews, deletedPlaylistContents
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
