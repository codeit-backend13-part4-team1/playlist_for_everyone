package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final ReviewRepository reviewRepository;
    private final ContentMapper contentMapper;
    private final FileService fileService;

    @Transactional
    public ContentDto create(ContentCreateRequest request, MultipartFile file) {
        // S3 업로드 및 파일 정보 저장
        FileInfo fileInfo = fileService.upload(file);
        String thumbnailUrl = fileService.getFileUrl(fileInfo.getId());

        Content content = Content.createAdminContent(
                request.type(),
                request.title(),
                request.description(),
                thumbnailUrl,
                fileInfo
        );

        normalizeTags(request.tags()).forEach(content::addTag);

        Content savedContent = contentRepository.save(content);

        return contentMapper.toDto(savedContent, 0.0, 0, 0L);
    }

    @Transactional
    public ContentDto update(UUID id, ContentUpdateRequest request, MultipartFile file) {
        Content content = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(Map.of("contentId", id)));

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

        // 파일이 전달된 경우에만 썸네일 수정
        if (file != null && !file.isEmpty()) {
            FileInfo oldFileInfo = content.getFileInfo();

            FileInfo newFileInfo = fileService.upload(file);
            String thumbnailUrl = fileService.getFileUrl(newFileInfo.getId());

            content.updateThumbnail(thumbnailUrl, newFileInfo);

            // 기존 파일 삭제
            if (oldFileInfo != null) {
                fileService.delete(oldFileInfo.getId());
            }
        }

        double averageRating = reviewRepository.findAverageRatingByContentId(id);
        int reviewCount = Math.toIntExact(reviewRepository.countByContentId(id));
        long watcherCount = 0L; // TODO: 시청 세션 기능 연동

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount);
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
