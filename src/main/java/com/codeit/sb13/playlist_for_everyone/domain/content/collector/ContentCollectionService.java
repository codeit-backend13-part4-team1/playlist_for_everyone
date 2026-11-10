package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

        Content existing = contentRepository
                .findByProviderAndTypeAndProviderItemId(
                        data.provider(),
                        data.type(),
                        data.providerItemId()
                )
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        int inserted = contentRepository.insertIfAbsent(
                UUID.randomUUID(),
                content.getType().name(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getProvider().name(),
                content.getProviderItemId(),
                LocalDateTime.now()
        );

        Content saved = contentRepository
                .findByProviderAndTypeAndProviderItemId(
                        data.provider(),
                        data.type(),
                        data.providerItemId()
                )
                .orElseThrow(() ->
                        new IllegalStateException("수집한 콘텐츠를 조회할 수 없습니다.")
                );

        if (inserted == 1) {
            tags.forEach(saved::addTag);
        }

        return saved;
    }
}