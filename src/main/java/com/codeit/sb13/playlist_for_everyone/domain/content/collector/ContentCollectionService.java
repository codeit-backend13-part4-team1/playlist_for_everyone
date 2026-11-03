package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ContentCollectionService {

    private final ContentRepository contentRepository;

    @Transactional
    public Content save(CollectedContentData data) {
        if (!StringUtils.hasText(data.title())) {
            throw new IllegalArgumentException("수집한 콘텐츠에는 제목이 필요합니다.");
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