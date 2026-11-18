package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.DpeWatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 서비스 테스트")
public class ContentServiceTest {

    @Mock
    private ContentReader reader;

    @Mock
    private ContentWriter writer;

    @Mock
    private DpeWatchingSessionService dpeWatchingSessionService;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private FileService fileService;

    @InjectMocks
    private ContentService contentService;

    @Test
    @DisplayName("콘텐츠 생성 후 Presigned URL을 포함한 DTO를 반환한다.")
    void create_success() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        MultipartFile file = mock(MultipartFile.class);

        String key = "contents/test-thumbnail.jpg";
        String presignedUrl = "https://example.com/thumbnail.jpg";

        ContentDto dto = new ContentDto(
                UUID.randomUUID(),
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null,
                List.of("드라마", "로맨스"),
                0.0, 0, 0L
        );

        given(writer.create(request, file))
                .willReturn(new ContentResult<>(dto, key));

        given(fileService.getFileUrlByKey(key))
                .willReturn(presignedUrl);

        ContentDto expected = new ContentDto(
                dto.id(),
                dto.type(),
                dto.title(),
                dto.description(),
                presignedUrl,
                dto.tags(),
                dto.averageRating(),
                dto.reviewCount(),
                dto.watcherCount()
        );

        given(contentMapper.toContentDto(dto, presignedUrl, 0L))
                .willReturn(expected);

        // when
        ContentDto result = contentService.create(request, file);

        // then
        // then
        assertThat(result).isSameAs(expected);
        assertThat(result.thumbnailUrl()).isEqualTo(presignedUrl);

        verify(writer).create(request, file);
        verify(fileService).getFileUrlByKey(key);
        verify(contentMapper).toContentDto(dto, presignedUrl, 0L);
        verifyNoInteractions(dpeWatchingSessionService);
    }

    @Test
    @DisplayName("외부 썸네일 URL이 있는 콘텐츠는 기존 URL을 반환한다.")
    void getContent_externalThumbnail() {
        // given
        UUID contentId = UUID.randomUUID();
        String thumbnailUrl = "https://example.com/thumbnail.jpg";
        long watcherCount = 15L;

        ContentDto dto = mock(ContentDto.class);
        ContentDto expected = mock(ContentDto.class);

        given(dto.thumbnailUrl()).willReturn(thumbnailUrl);
        given(reader.getById(contentId))
                .willReturn(new ContentResult(dto, null));
        given(dpeWatchingSessionService.getCurrentViewerCount(contentId))
                .willReturn(watcherCount);
        given(contentMapper.toContentDto(dto, thumbnailUrl, watcherCount))
                .willReturn(expected);

        // when
        ContentDto result = contentService.getById(contentId);

        // then
        assertThat(result).isSameAs(expected);

        verify(reader).getById(contentId);
    }

    @Test
    @DisplayName("S3 썸네일이 있는 콘텐츠는 Presigned URL을 반환한다.")
    void getContent_s3Thumbnail() {
        // given
        UUID contentId = UUID.randomUUID();
        String fileKey = "contents/test-thumbnail.jpg";
        String presignedUrl = "https://s3.example.com/test-thumbnail.jpg";
        long watcherCount = 15L;

        ContentDto dto = mock(ContentDto.class);
        ContentDto expected = mock(ContentDto.class);

        given(reader.getById(contentId))
                .willReturn(new ContentResult(dto, fileKey));

        given(fileService.getFileUrlByKey(fileKey))
                .willReturn(presignedUrl);

        given(dpeWatchingSessionService.getCurrentViewerCount(contentId))
                .willReturn(watcherCount);

        given(contentMapper.toContentDto(dto, presignedUrl, watcherCount))
                .willReturn(expected);

        // when
        ContentDto result = contentService.getById(contentId);

        // then
        assertThat(result).isSameAs(expected);

        verify(reader).getById(contentId);
        verify(dpeWatchingSessionService).getCurrentViewerCount(contentId);
        verify(fileService).getFileUrlByKey(fileKey);
        verify(contentMapper).toContentDto(dto, presignedUrl, watcherCount);
    }

    @Test
    @DisplayName("콘텐츠 일괄 조회 시 S3 썸네일을 Presigned URL로 변환한다.")
    void getSummariesByIds_s3Thumbnail() {
        // given
        UUID contentId = UUID.randomUUID();
        List<UUID> contentIds = List.of(contentId);

        String fileKey = "contents/test.jpg";
        String presignedUrl = "https://example.com/presigned.jpg";

        ContentSummary summary = new ContentSummary(
                contentId,
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                null,
                List.of("액션"),
                4.5,
                2
        );

        ContentSummary expected = new ContentSummary(
                contentId,
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                presignedUrl,
                List.of("액션"),
                4.5,
                2
        );

        given(reader.getSummariesByIds(contentIds))
                .willReturn(List.of(new ContentResult<>(summary, fileKey)));

        given(fileService.getFileUrlByKey(fileKey))
                .willReturn(presignedUrl);

        given(contentMapper.toSummary(summary, presignedUrl))
                .willReturn(expected);

        // when
        List<ContentSummary> results =
                contentService.getSummariesByIds(contentIds);

        // then
        assertThat(results).containsExactly(expected);

        verify(reader).getSummariesByIds(contentIds);
        verify(fileService).getFileUrlByKey(fileKey);
        verify(contentMapper).toSummary(summary, presignedUrl);
    }

    @Test
    @DisplayName("콘텐츠 일괄 조회 시 외부 썸네일 URL은 그대로 유지한다.")
    void getSummariesByIds_externalThumbnail() {
        // given
        UUID contentId = UUID.randomUUID();
        List<UUID> contentIds = List.of(contentId);

        String externalUrl = "https://example.com/external.jpg";

        ContentSummary summary = new ContentSummary(
                contentId,
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                externalUrl,
                List.of("액션"),
                4.5,
                2
        );

        given(reader.getSummariesByIds(contentIds))
                .willReturn(List.of(new ContentResult<>(summary, null)));

        given(contentMapper.toSummary(summary, externalUrl))
                .willReturn(summary);

        // when
        List<ContentSummary> results =
                contentService.getSummariesByIds(contentIds);

        // then
        assertThat(results).containsExactly(summary);

        verify(reader).getSummariesByIds(contentIds);
        verify(contentMapper).toSummary(summary, externalUrl);
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("콘텐츠 수정 후 Presigned URL을 포함한 DTO를 반환한다.")
    void update_success() {
        // given
        UUID contentId = UUID.randomUUID();
        long watcherCount = 15L;

        ContentUpdateRequest request = new ContentUpdateRequest(
                "수정된 제목",
                "수정된 설명",
                List.of("드라마")
        );

        String key = "contents/updated-thumbnail.jpg";
        String presignedUrl = "https://example.com/updated.jpg";

        ContentDto dto = mock(ContentDto.class);
        ContentDto expected = mock(ContentDto.class);

        given(writer.update(contentId, request, null))
                .willReturn(new ContentResult(dto, key));

        given(fileService.getFileUrlByKey(key))
                .willReturn(presignedUrl);

        given(dpeWatchingSessionService.getCurrentViewerCount(contentId))
                .willReturn(watcherCount);

        given(contentMapper.toContentDto(dto, presignedUrl, watcherCount))
                .willReturn(expected);

        // when
        ContentDto result = contentService.update(contentId, request, null);

        // then
        assertThat(result).isSameAs(expected);

        verify(writer).update(contentId, request, null);
        verify(dpeWatchingSessionService).getCurrentViewerCount(contentId);
        verify(fileService).getFileUrlByKey(key);
        verify(contentMapper).toContentDto(dto, presignedUrl, watcherCount);
    }

    @Test
    @DisplayName("콘텐츠 삭제를 Writer에 위임한다.")
    void delete_success() {
        // given
        UUID contentId = UUID.randomUUID();

        // when
        contentService.deleteById(contentId);

        // then
        verify(writer).deleteById(contentId);
    }

}
