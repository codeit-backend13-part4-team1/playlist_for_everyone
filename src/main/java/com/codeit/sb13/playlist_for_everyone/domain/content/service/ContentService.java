package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentMapper contentMapper;

    @Transactional
    public ContentDto create(ContentCreateRequest request, MultipartFile file) {
        Content content = Content.createAdminContent(
                request.type(),
                request.title(),
                request.description(),
                "temporary-thumbnail-url"   // TODO: S3 파일 업로드 연동 후 실제 URL로 변경
        );

        Set<String> seen = new HashSet<>();

        // 태그가 없는 경우 빈 리스트로 처리
        // 중간 공백을 제외한 문자열이 같으면 먼저 입력된 태그만 저장
        List<String> tags = Optional.ofNullable(request.tags())
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(tag -> !tag.isBlank())
                .filter(tag -> seen.add(tag.replaceAll("\\s+", "")))
                .toList();

        for (String tag : tags) {
            content.addTag(tag);
        }

        Content savedContent = contentRepository.save(content);

        return contentMapper.toDto(savedContent, 0.0, 0, 0);
    }

}
