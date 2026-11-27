package com.codeit.sb13.playlist_for_everyone.domain.content.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchCacheEvictService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentIndexAsyncProcessor 테스트")
class ContentIndexAsyncProcessorTest {

    @Mock
    private ContentIndexService contentIndexService;

    @Mock
    private ContentSearchCacheEvictService cacheEvictService;

    @InjectMocks
    private ContentIndexAsyncProcessor asyncProcessor;

    @Test
    @DisplayName("콘텐츠 문서 저장 이후 검색 캐시를 무효화한다.")
    void upsert_success() {
        // given
        UUID contentId = UUID.randomUUID();

        // when
        asyncProcessor.upsert(contentId);

        // then
        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).upsert(contentId);
        inOrder.verify(cacheEvictService).clearSearchCache();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("콘텐츠 문서 저장 실패 시에도 검색 캐시를 무효화한다.")
    void upsert_indexFailure() {
        // given
        UUID contentId = UUID.randomUUID();

        doThrow(new RuntimeException("색인 실패"))
                .when(contentIndexService).upsert(contentId);

        // when & then
        assertThatThrownBy(() -> asyncProcessor.upsert(contentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("색인 실패");

        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).upsert(contentId);
        inOrder.verify(cacheEvictService).clearSearchCache();
    }

    @Test
    @DisplayName("콘텐츠 문서 삭제 이후 검색 캐시를 무효화한다.")
    void delete_success() {
        // given
        UUID contentId = UUID.randomUUID();

        // when
        asyncProcessor.delete(contentId);

        // then
        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).delete(contentId);
        inOrder.verify(cacheEvictService).clearSearchCache();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("콘텐츠 문서 삭제 실패 시에도 검색 캐시를 무효화한다.")
    void delete_indexFailure() {
        // given
        UUID contentId = UUID.randomUUID();

        doThrow(new RuntimeException("색인 삭제 실패"))
                .when(contentIndexService).delete(contentId);

        // when & then
        assertThatThrownBy(() -> asyncProcessor.delete(contentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("색인 삭제 실패");

        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).delete(contentId);
        inOrder.verify(cacheEvictService).clearSearchCache();
    }

    @Test
    @DisplayName("콘텐츠 일괄 색인 이후 검색 캐시를 무효화한다.")
    void upsertAll_success() {
        // given
        List<UUID> contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // when
        asyncProcessor.upsertAll(contentIds);

        // then
        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).upsertAll(contentIds);
        inOrder.verify(cacheEvictService).clearSearchCache();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("콘텐츠 일괄 색인 실패 시에도 검색 캐시를 무효화한다.")
    void upsertAll_indexFailure() {
        // given
        List<UUID> contentIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        doThrow(new RuntimeException("일괄 색인 실패")).when(contentIndexService)
                .upsertAll(contentIds);

        // when & then
        assertThatThrownBy(() -> asyncProcessor.upsertAll(contentIds))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("일괄 색인 실패");

        InOrder inOrder = inOrder(contentIndexService, cacheEvictService);

        inOrder.verify(contentIndexService).upsertAll(contentIds);
        inOrder.verify(cacheEvictService).clearSearchCache();
    }

}