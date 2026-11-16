package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 검색 서비스 테스트")
public class ContentSearchServiceTest {

    @Mock
    private ContentSearchCacheService cacheService;

    @Mock
    private FileService fileService;

    @Mock
    private ContentMapper contentMapper;

    @InjectMocks
    private ContentSearchService contentSearchService;

    private ContentSearchCondition condition;

    @BeforeEach
    void setUp() {
        condition = new ContentSearchCondition(
                null, null, null, null, null,
                2, SortDirection.DESCENDING, "createdAt"
        );
    }

    @Test
    @DisplayName("S3 썸네일은 Presigned URL로 변환한다.")
    void search_s3Thumbnail() {
        // given
        String fileKey = "contents/test.jpg";
        String presignedUrl = "https://example.com/presigned.jpg";

        ContentDto dto = createDto(null);
        ContentDto expected = createDto(presignedUrl);

        given(cacheService.search(condition))
                .willReturn(createResponse(
                        List.of(new ContentResult<>(dto, fileKey))
                ));
        given(fileService.getFileUrlByKey(fileKey))
                .willReturn(presignedUrl);
        given(contentMapper.toContentDto(dto, presignedUrl, null))
                .willReturn(expected);

        // when
        CursorResponse<ContentDto> result = contentSearchService.search(condition);

        // then
        assertThat(result.data()).containsExactly(expected);

        verify(cacheService).search(condition);
        verify(fileService).getFileUrlByKey(fileKey);
        verify(contentMapper).toContentDto(dto, presignedUrl, null);
    }

    @Test
    @DisplayName("외부 썸네일 URL은 그대로 유지한다.")
    void search_externalThumbnail() {
        // given
        String externalUrl = "https://example.com/external.jpg";

        ContentDto dto = createDto(externalUrl);

        given(cacheService.search(condition))
                .willReturn(createResponse(
                        List.of(new ContentResult<>(dto, null))
                ));
        given(contentMapper.toContentDto(dto, externalUrl, null))
                .willReturn(dto);

        // when
        CursorResponse<ContentDto> result =
                contentSearchService.search(condition);

        // then
        assertThat(result.data()).containsExactly(dto);

        verify(cacheService).search(condition);
        verify(contentMapper).toContentDto(dto, externalUrl, null);
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("DTO 변환 후에도 페이지네이션 정보를 유지한다.")
    void search_preservesPagination() {
        // given
        UUID nextIdAfter = UUID.randomUUID();
        ContentDto dto = createDto(null);

        CursorResponse<ContentResult<ContentDto>> cachedResponse = new CursorResponse<>(
                List.of(new ContentResult<>(dto, null)),
                "2026-11-16T10:00:00",
                nextIdAfter,
                true,
                3L,
                "createdAt",
                SortDirection.DESCENDING
        );

        given(cacheService.search(condition)).willReturn(cachedResponse);
        given(contentMapper.toContentDto(dto, null, null)).willReturn(dto);

        // when
        CursorResponse<ContentDto> result = contentSearchService.search(condition);

        // then
        assertThat(result.data()).containsExactly(dto);
        assertThat(result.nextCursor()).isEqualTo(cachedResponse.nextCursor());
        assertThat(result.nextIdAfter()).isEqualTo(nextIdAfter);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalCount()).isEqualTo(3L);
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
    }

    private ContentDto createDto(String thumbnailUrl) {
        return new ContentDto(
                UUID.randomUUID(),
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                thumbnailUrl,
                List.of("액션"),
                4.5,
                2,
                0L
        );
    }

    private CursorResponse<ContentResult<ContentDto>> createResponse(
            List<ContentResult<ContentDto>> contents
    ) {
        return new CursorResponse<>(
                contents,
                null,
                null,
                false,
                contents.size(),
                "createdAt",
                SortDirection.DESCENDING
        );
    }

}
