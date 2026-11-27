package com.codeit.sb13.playlist_for_everyone.domain.content.event;

import com.codeit.sb13.playlist_for_everyone.domain.content.config.ContentIndexAsyncProcessor;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexEventListener;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexUpsertEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchCacheEvictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentIndexEventListener 테스트")
class ContentIndexEventListenerTest {

    @Mock
    private ContentIndexAsyncProcessor asyncProcessor;

    @Mock
    private ContentSearchCacheEvictService cacheEvictService;

    @InjectMocks
    private ContentIndexEventListener contentIndexEventListener;

    @Test
    @DisplayName("콘텐츠 생성·수정 이벤트 수신 시 비동기 색인 작업을 제출한다.")
    void handleUpsert_success() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentIndexUpsertEvent event = new ContentIndexUpsertEvent(contentId);

        // when
        contentIndexEventListener.handleUpsert(event);

        // then
        verify(asyncProcessor).upsert(contentId);
        verifyNoMoreInteractions(asyncProcessor);
        verifyNoInteractions(cacheEvictService);
    }

    @Test
    @DisplayName("콘텐츠 삭제 이벤트 수신 시 비동기 삭제 작업을 제출한다.")
    void handleDelete_success() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentIndexDeleteEvent event =
                new ContentIndexDeleteEvent(contentId);

        // when
        contentIndexEventListener.handleDelete(event);

        // then
        verify(asyncProcessor).delete(contentId);
        verifyNoMoreInteractions(asyncProcessor);
        verifyNoInteractions(cacheEvictService);
    }

    @Test
    void 배치에서_신규_저장한_콘텐츠가_있으면_색인_작업을_제출한다() {
        var contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        new ApplicationContextRunner()
                .withBean(ContentIndexAsyncProcessor.class, () -> asyncProcessor)
                .withBean(ContentSearchCacheEvictService.class, () -> cacheEvictService)
                .withUserConfiguration(ContentIndexEventListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(3L, 0L, contentIds));

                    verify(asyncProcessor).upsertAll(contentIds);
                    verifyNoInteractions(cacheEvictService);
                });
    }

    @Test
    void 배치에서_신규_저장과_갱신이_없으면_색인_작업을_제출하지_않는다() {
        new ApplicationContextRunner()
                .withBean(ContentIndexAsyncProcessor.class, () -> asyncProcessor)
                .withBean(ContentSearchCacheEvictService.class, () -> cacheEvictService)
                .withUserConfiguration(ContentIndexEventListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(0L, 0L, List.of()));

                    verifyNoInteractions(asyncProcessor, cacheEvictService);
                });
    }

    @Test
    void 배치에서_기존_콘텐츠만_갱신해도_색인_작업을_제출한다() {
        var contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        new ApplicationContextRunner()
                .withBean(ContentIndexAsyncProcessor.class, () -> asyncProcessor)
                .withBean(ContentSearchCacheEvictService.class, () -> cacheEvictService)
                .withUserConfiguration(ContentIndexEventListener.class)
                .run(context -> {
                    context.publishEvent(new ContentCollectionFinishedEvent(0L, 2L, contentIds));

                    verify(asyncProcessor).upsertAll(contentIds);
                    verifyNoInteractions(cacheEvictService);

                });
    }

    @Test
    @DisplayName("콘텐츠 일괄 색인 작업 제출 실패 시 검색 캐시를 무효화한다.")
    void handleCollection_taskRejected() {
        // given
        List<UUID> contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        ContentCollectionFinishedEvent event =
                new ContentCollectionFinishedEvent(2L, 0L, contentIds);

        doThrow(new TaskRejectedException("색인 작업 제출 실패"))
                .when(asyncProcessor).upsertAll(contentIds);

        // when & then
        assertThatCode(() -> contentIndexEventListener.handleCollection(event))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(asyncProcessor, cacheEvictService);

        inOrder.verify(asyncProcessor).upsertAll(contentIds);
        inOrder.verify(cacheEvictService).clearSearchCache();
    }

}
