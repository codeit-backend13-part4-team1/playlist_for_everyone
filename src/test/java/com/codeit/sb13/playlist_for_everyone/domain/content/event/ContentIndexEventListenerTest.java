package com.codeit.sb13.playlist_for_everyone.domain.content.event;

import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexEventListener;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexUpsertEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchCacheEvictService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentIndexEventListener 테스트")
class ContentIndexEventListenerTest {

    @Mock
    private ContentIndexService contentIndexService;

    @Mock
    private ContentSearchCacheEvictService cacheEvictService;

    @InjectMocks
    private ContentIndexEventListener contentIndexEventListener;

    @Test
    @DisplayName("콘텐츠 생성·수정 이벤트 수신 시 문서를 저장한다.")
    void handleUpsert_success() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentIndexUpsertEvent event =
                new ContentIndexUpsertEvent(contentId);

        // when
        contentIndexEventListener.handleUpsert(event);

        // then
        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).upsert(contentId);
        inOrder.verify(cacheEvictService).clearSearchCache();

        verify(contentIndexService).upsert(contentId);
        verifyNoMoreInteractions(contentIndexService);
    }

    @Test
    @DisplayName("콘텐츠 삭제 이벤트 수신 시 문서를 삭제한다.")
    void handleDelete_success() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentIndexDeleteEvent event =
                new ContentIndexDeleteEvent(contentId);

        // when
        contentIndexEventListener.handleDelete(event);

        // then
        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).delete(contentId);
        inOrder.verify(cacheEvictService).clearSearchCache();
    }

    @Test
    void 배치에서_신규_저장한_콘텐츠가_있으면_캐시를_삭제한다() {
        var contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        new ApplicationContextRunner()
                .withBean(ContentIndexService.class, () -> contentIndexService)
                .withBean(ContentSearchCacheEvictService.class, () -> cacheEvictService)
                .withUserConfiguration(ContentIndexEventListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(3L, 0L, contentIds));

                    verify(cacheEvictService).clearSearchCache();
                });
    }

    @Test
    void 배치에서_신규_저장과_갱신이_없으면_캐시를_삭제하지_않는다() {
        new ApplicationContextRunner()
                .withBean(ContentIndexService.class, () -> contentIndexService)
                .withBean(ContentSearchCacheEvictService.class, () -> cacheEvictService)
                .withUserConfiguration(ContentIndexEventListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(0L, 0L, List.of()));

                    verifyNoInteractions(contentIndexService, cacheEvictService);
                });
    }

    @Test
    void 배치에서_기존_콘텐츠만_갱신해도_캐시를_삭제한다() {
        var contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        new ApplicationContextRunner()
                .withBean(ContentIndexService.class, () -> contentIndexService)
                .withBean(ContentSearchCacheEvictService.class, () -> cacheEvictService)
                .withUserConfiguration(ContentIndexEventListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(0L, 2L, contentIds));

                    verify(cacheEvictService).clearSearchCache();
                });
    }

}
