package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.ContentCollectionResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.exception.CollectedContentValidationException;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexUpsertEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentCollectionService {

    private final ContentRepository contentRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ContentCollectionResult saveForBatch(CollectedContentData data) {
        return saveContent(data);
    }

    @Transactional
    public Content save(CollectedContentData data) {
        ContentCollectionResult result = saveContent(data);

        if (result.changed()) {
            eventPublisher.publishEvent(new ContentIndexUpsertEvent(result.content().getId()));
        }

        return result.content();
    }

    private ContentCollectionResult saveContent(CollectedContentData data) {

        if (!StringUtils.hasText(data.title())) {
            throw new CollectedContentValidationException("수집한 콘텐츠에는 제목이 필요합니다.");
        }

        if (data.title().codePointCount(0, data.title().length()) > 100) {
            throw new CollectedContentValidationException("수집한 콘텐츠 제목은 100자 이하여야 합니다.");
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
                throw new CollectedContentValidationException("수집한 콘텐츠 태그는 50자 이하여야 합니다.");
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
            return updateExistingContent(existing, data, tags);
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
            return new ContentCollectionResult(saved, true, false);
        }

        return updateExistingContent(saved, data, tags);
    }

    private ContentCollectionResult updateExistingContent(
            Content existing,
            CollectedContentData data,
            List<String> tags
    ) {
        var existingTags = existing.getTags().stream()
                .map(ContentTag::getTag)
                .collect(Collectors.toSet());

        boolean updated = !Objects.equals(existing.getTitle(), data.title())
                || !Objects.equals(existing.getDescription(), data.description())
                || !Objects.equals(existing.getThumbnailUrl(), data.thumbnailUrl())
                || !existingTags.equals(new HashSet<>(tags));

        if (updated) {
            existing.update(data.title(), data.description(), tags);
            // API 썸네일 URL은 갱신하되, 관리자가 업로드한 이미지는 유지한다.
            // 업로드 이미지가 있으면 콘텐츠 조회 시 해당 이미지를 우선 사용한다.
            existing.updateThumbnail(
                    data.thumbnailUrl(),
                    existing.getFileInfo()
            );
        }

        return new ContentCollectionResult(existing, false, updated);
    }
}