package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
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

    private final ContentRepository contentRepository;
    private final ReviewRepository reviewRepository;
    private final WatchingSessionService watchingSessionService;
    private final PlaylistContentRepository playlistContentRepository;
    private final ContentMapper contentMapper;

    @Transactional
    public ContentDto create(ContentCreateRequest request, MultipartFile file) {
        Content content = Content.createAdminContent(
                request.type(),
                request.title(),
                request.description(),
                "temporary-thumbnail-url"   // TODO: S3 파일 업로드 연동 후 실제 URL로 변경
        );

        List<String> tags = normalizeTags(request.tags());
        tags.forEach(content::addTag);

        Content savedContent = contentRepository.save(content);

        return contentMapper.toDto(savedContent, 0.0, 0, 0L);
    }

    @Transactional(readOnly = true)
    public ContentDto getById(UUID contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(
                        Map.of("contentId", contentId)
                ));

        double averageRating = reviewRepository.findAverageRatingByContentId(contentId);
        int reviewCount = Math.toIntExact(reviewRepository.countByContentId(contentId));
        long watcherCount = watchingSessionService.getCurrentViewerCount(contentId);

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount);
    }

    @Transactional
    public ContentDto update(UUID contentId, ContentUpdateRequest request, MultipartFile file) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(Map.of("contentId", contentId)));

        List<String> tags = request.tags() == null
                ? null
                : normalizeTags(request.tags());

        // TODO: 이미지 업로드 구현 후 새 URL 적용
        String thumbnailUrl = null;

        content.update(
                request.title(),
                request.description(),
                thumbnailUrl,
                tags
        );

        double averageRating = reviewRepository.findAverageRatingByContentId(contentId);
        int reviewCount = Math.toIntExact(reviewRepository.countByContentId(contentId));
        long watcherCount = watchingSessionService.getCurrentViewerCount(contentId);

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount);
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

        log.debug(
                "콘텐츠 삭제: contentId={}, 삭제된 리뷰={}, 삭제된 플레이리스트 연결={}",
                contentId, deletedReviews, deletedPlaylistContents
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
