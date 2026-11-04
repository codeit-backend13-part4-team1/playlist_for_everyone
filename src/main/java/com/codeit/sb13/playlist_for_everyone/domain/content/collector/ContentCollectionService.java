package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentCollectionService {

    private final ContentRepository contentRepository;

    @Transactional
    public Content save(CollectedContentData data) {
        if (!StringUtils.hasText(data.title())) {
            throw new IllegalArgumentException("수집한 콘텐츠에는 제목이 필요합니다.");
        }

        if (data.title().codePointCount(0, data.title().length()) > 100) {
            throw new IllegalArgumentException("수집한 콘텐츠 제목은 100자 이하여야 합니다.");
        }

        List<String> tags = data.tags() == null
                ? List.of()
                : data.tags().stream()
                  .filter(StringUtils::hasText)
                  .map(String::strip)
                  .distinct()
                  .toList();

        for (String tag : tags) {
            if (tag.codePointCount(0, tag.length()) > 50) {
                throw new IllegalArgumentException("수집한 콘텐츠 태그는 50자 이하여야 합니다.");
            }
        }

        Content content = Content.createCollectedContent(
                data.type(),
                data.title(),
                data.description(),
                data.thumbnailUrl(),
                data.provider(),
                data.providerItemId()
        );

        if (data.tags() != null) {
            data.tags().stream()
                    .filter(StringUtils::hasText)
                    .map(String::strip)
                    .distinct()
                    .forEach(content::addTag);
        }

        return contentRepository.save(content);
    }
}