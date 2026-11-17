
package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Content 검색 쿼리 서비스 테스트")
class ContentSearchQueryServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private WatchingSessionService watchingSessionService;

    @Mock
    private ContentMapper contentMapper;

    @InjectMocks
    private ContentSearchQueryService queryService;

    private ContentSearchCondition condition;
    private Content content1;
    private Content content2;
    private Content content3;

    @BeforeEach
    void setUp() {
        condition = new ContentSearchCondition(
                null, null, null, null, null,
                2, SortDirection.DESCENDING, "rate"
        );

        content1 = Content.createAdminContent(
                ContentType.tvSeries,
                "드라마1",
                "드라마설명1",
                null
        );
        content2 = Content.createAdminContent(
                ContentType.movie,
                "영화1",
                "영화설명1",
                null
        );
        content3 = Content.createAdminContent(
                ContentType.sport,
                "스포츠1",
                "스포츠설명1",
                null
        );

        // 테스트용 UUID 설정
        ReflectionTestUtils.setField(content1, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(content2, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(content3, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("콘텐츠 검색 시 요청한 개수만 반환하고 다음 페이지 커서를 생성한다.")
    void searchContents_returnsCursorPageWithNextCursor() {
        // given
        given(contentRepository.searchByDatabase(condition))
                .willReturn(List.of(
                        new ContentSearchResult(content1, 5.0, 2),
                        new ContentSearchResult(content2, 4.5, 3),
                        new ContentSearchResult(content3, 3.0, 1)
                ));

        // when
        var result = queryService.search(condition);

        // then
        assertThat(result.data()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("4.5");
    }

    @Test
    @DisplayName("평균 평점 정렬 시 DB Repository를 호출하고 시청자 수를 조회하지 않는다")
    void searchContents_byRate() {
        // given
        ContentSearchCondition condition = conditionWithSortBy("rate");

        // when
        queryService.search(condition);

        // then
        verify(contentRepository).searchByDatabase(condition);
        verifyNoInteractions(watchingSessionService);
    }

    @Test
    @DisplayName("시청자 수 정렬 시 전용 Repository 메서드를 호출한다")
    void searchContents_byWatcherCount() {
        // given
        ContentSearchCondition condition = conditionWithSortBy("watcherCount");

        // when
        queryService.search(condition);

        // then
        verify(contentRepository)
                .searchForWatcherCount(condition);

        verify(contentRepository).searchForWatcherCount(condition);
        verify(contentRepository, never()).searchByDatabase(any());
    }

    @Test
    @DisplayName("시청자 수 내림차순으로 정렬하고 마지막 콘텐츠의 시청자 수를 커서로 반환한다")
    void searchContents_watcherCount() {
        // given
        ContentSearchCondition condition = conditionWithSortBy("watcherCount");

        given(contentRepository.searchForWatcherCount(condition))
                .willReturn(List.of(
                        new ContentSearchResult(content1, 5.0, 2),
                        new ContentSearchResult(content2, 4.5, 3),
                        new ContentSearchResult(content3, 3.0, 1)
                ));

        given(watchingSessionService.getCurrentViewerCountWithContents(anyList()))
                .willReturn(Map.of(
                        content1.getId().toString(), 5L,
                        content2.getId().toString(), 30L,
                        content3.getId().toString(), 15L
                ));

        // when
        var result = queryService.search(condition);

        // then
        assertThat(result.data()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("15");
        assertThat(result.nextIdAfter()).isEqualTo(content3.getId());

        // 시청자 수 내림차순으로 DTO를 변환하고, 시청자 수는 0L로 전달하는지 확인
        InOrder inOrder = inOrder(contentMapper);

        inOrder.verify(contentMapper).toDto(
                eq(content2), any(), eq(4.5), eq(3), eq(30L)
        );

        inOrder.verify(contentMapper).toDto(
                eq(content3), any(), eq(3.0), eq(1), eq(15L)
        );

        verify(contentRepository).searchForWatcherCount(condition);
        verify(contentRepository, never()).searchByDatabase(any());
    }

    private ContentSearchCondition conditionWithSortBy(String sortBy) {
        return new ContentSearchCondition(
                condition.typeEqual(),
                condition.keywordLike(),
                condition.tagsIn(),
                condition.cursor(),
                condition.idAfter(),
                condition.limit(),
                condition.sortDirection(),
                sortBy
        );
    }

}
