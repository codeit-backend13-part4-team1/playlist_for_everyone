package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ContentSearchCacheService.class,
        ContentSearchCacheServiceTest.TestCacheConfig.class
})
@DisplayName("콘텐츠 검색 캐시 서비스 테스트")
public class ContentSearchCacheServiceTest {

    @TestConfiguration
    @EnableCaching
    static class TestCacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConfig.CONTENT_SEARCH
            );
        }
    }

    @Autowired
    private ContentSearchCacheService cacheService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ContentSearchQueryService queryService;

    private ContentSearchCondition condition;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.CONTENT_SEARCH)).clear();
        reset(queryService);

        condition = new ContentSearchCondition(
                null, null, null, null, null,
                2, SortDirection.DESCENDING, "rate"
        );

    }

    @Test
    @DisplayName("최초 검색 시 QueryService를 호출한다")
    void search_cacheMiss() {
        // given
        CursorResponse<ContentResult> response = createResponse();

        given(queryService.search(condition)).willReturn(response);

        // when
        CursorResponse<ContentResult> result =
                cacheService.search(condition);

        // then
        assertThat(result).isEqualTo(response);
        verify(queryService, times(1)).search(condition);
    }

    @Test
    @DisplayName("동일한 검색 조건은 캐시된 결과를 반환한다")
    void search_cacheHit() {
        // given
        CursorResponse<ContentResult> response = createResponse();

        given(queryService.search(condition)).willReturn(response);

        // when
        CursorResponse<ContentResult> first =
                cacheService.search(condition);

        CursorResponse<ContentResult> second =
                cacheService.search(condition);

        // then
        assertThat(first).isEqualTo(response);
        assertThat(second).isEqualTo(response);
        verify(queryService, times(1)).search(condition);
    }

    private ContentResult createResult() {
        ContentDto dto = new ContentDto(
                UUID.randomUUID(),
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                null,
                List.of("액션"),
                4.5,
                2,
                0L
        );

        return new ContentResult(
                dto,
                "contents/test-thumbnail.jpg"
        );
    }

    private CursorResponse<ContentResult> createResponse() {
        return new CursorResponse<>(
                List.of(createResult()),
                null,
                null,
                false,
                1L,
                "rate",
                SortDirection.DESCENDING
        );
    }

}
