package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 검색 서비스 테스트")
public class ContentSearchServiceTest {

    @Mock
    private ContentSearchCacheService cacheService;

    @Mock
    private WatchingSessionService watchingSessionService;

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
        given(watchingSessionService.getCurrentViewerCountWithContents(
                List.of(dto.id())
        )).willReturn(Map.of(dto.id().toString(), 100L));
        given(contentMapper.toContentDto(dto, presignedUrl, 100L))
                .willReturn(expected);

        // when
        CursorResponse<ContentDto> result = contentSearchService.search(condition);

        // then
        assertThat(result.data()).containsExactly(expected);

        verify(cacheService).search(condition);
        verify(fileService).getFileUrlByKey(fileKey);
        verify(contentMapper).toContentDto(dto, presignedUrl, 100L);
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
        given(watchingSessionService.getCurrentViewerCountWithContents(
                List.of(dto.id())
        )).willReturn(Map.of(dto.id().toString(), 100L));
        given(contentMapper.toContentDto(dto, externalUrl, 100L))
                .willReturn(dto);

        // when
        CursorResponse<ContentDto> result =
                contentSearchService.search(condition);

        // then
        assertThat(result.data()).containsExactly(dto);

        verify(cacheService).search(condition);
        verify(contentMapper).toContentDto(dto, externalUrl, 100L);
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
        given(watchingSessionService.getCurrentViewerCountWithContents(
                List.of(dto.id())
        )).willReturn(Map.of(dto.id().toString(), 100L));
        given(contentMapper.toContentDto(dto, null, 100L)).willReturn(dto);

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

    @Test
    @DisplayName("동일한 캐시 결과에서도 최신 시청자 수를 반영한다.")
    void search_updatesWatcherCount() {
        // given
        ContentDto dto = createDto(null);

        given(cacheService.search(condition))
                .willReturn(createResponse(
                        List.of(new ContentResult<>(dto, null))
                ));

        given(watchingSessionService.getCurrentViewerCountWithContents(
                List.of(dto.id()))).willReturn(
                Map.of(dto.id().toString(), 5L),
                Map.of(dto.id().toString(), 10L)
        );

        ContentDto firstDto = new ContentDto(
                dto.id(), dto.type(), dto.title(),
                dto.description(), dto.thumbnailUrl(),
                dto.tags(), dto.averageRating(),
                dto.reviewCount(), 5L
        );

        ContentDto secondDto = new ContentDto(
                dto.id(), dto.type(), dto.title(),
                dto.description(), dto.thumbnailUrl(),
                dto.tags(), dto.averageRating(),
                dto.reviewCount(), 10L
        );

        given(contentMapper.toContentDto(dto, null, 5L))
                .willReturn(firstDto);

        given(contentMapper.toContentDto(dto, null, 10L))
                .willReturn(secondDto);

        // when
        CursorResponse<ContentDto> first =
                contentSearchService.search(condition);

        CursorResponse<ContentDto> second =
                contentSearchService.search(condition);

        // then
        assertThat(first.data().get(0).watcherCount()).isEqualTo(5L);
        assertThat(second.data().get(0).watcherCount()).isEqualTo(10L);

        verify(cacheService, times(2)).search(condition);
        verify(watchingSessionService, times(2))
                .getCurrentViewerCountWithContents(List.of(dto.id()));
    }

    @Test
    @DisplayName("검색 결과가 없으면 시청자 수와 파일 URL을 조회하지 않는다.")
    void search_emptyResult() {
        // given
        given(cacheService.search(condition))
                .willReturn(createResponse(List.of()));

        // when
        CursorResponse<ContentDto> result =
                contentSearchService.search(condition);

        // then
        assertThat(result.data()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.totalCount()).isZero();

        verifyNoInteractions(
                watchingSessionService,
                fileService,
                contentMapper
        );
    }

    @Test
    @DisplayName("시청자 수 정렬 시 Redis를 재조회하지 않고 기존 시청자 수를 유지한다.")
    void search_watcherCountDoesNotReload() {
        // given
        ContentSearchCondition watcherCondition = new ContentSearchCondition(
                null, null, null, null, null,
                2, SortDirection.DESCENDING, "watcherCount"
        );

        ContentDto dto = createDto(null);

        ContentDto sortedDto = new ContentDto(
                dto.id(),
                dto.type(),
                dto.title(),
                dto.description(),
                dto.thumbnailUrl(),
                dto.tags(),
                dto.averageRating(),
                dto.reviewCount(),
                30L
        );

        given(cacheService.search(watcherCondition))
                .willReturn(createResponse(
                        List.of(new ContentResult<>(sortedDto, null))
                ));

        given(contentMapper.toContentDto(sortedDto, null, 30L))
                .willReturn(sortedDto);

        // when
        CursorResponse<ContentDto> result =
                contentSearchService.search(watcherCondition);

        // then
        assertThat(result.data()).containsExactly(sortedDto);
        assertThat(result.data().get(0).watcherCount()).isEqualTo(30L);

        verify(cacheService).search(watcherCondition);
        verify(contentMapper).toContentDto(sortedDto, null, 30L);
        verifyNoInteractions(watchingSessionService);
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
