
package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("Content 검색 서비스 테스트")
class ContentSearchQueryServiceTest {

    @Mock
    private ContentRepository contentRepository;

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
    }

    @Test
    @DisplayName("콘텐츠 검색 시 요청한 개수만 반환하고 다음 페이지 커서를 생성한다.")
    void searchContents_returnsCursorPageWithNextCursor() {
        // given
        given(contentRepository.search(condition))
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
}
