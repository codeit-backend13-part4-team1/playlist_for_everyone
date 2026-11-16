package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Content 서비스 테스트")
public class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ContentReader reader;

    @Mock
    private ContentWriter writer;

    @Mock
    private PlaylistContentRepository playlistContentRepository;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private FileService fileService;

    @InjectMocks
    private ContentService contentService;

    private FileInfo mockFileUpload(MultipartFile file) {
        FileInfo fileInfo = mock(FileInfo.class);
        given(fileService.upload(file)).willReturn(fileInfo);
        return fileInfo;
    }

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
                .willReturn(new ContentResult(dto, key));

        given(fileService.getFileUrlByKey(key))
                .willReturn(presignedUrl);

        given(contentMapper.withThumbnailUrl(dto, presignedUrl))
                .willReturn(new ContentDto(
                        dto.id(), dto.type(), dto.title(),
                        dto.description(), presignedUrl, dto.tags(),
                        dto.averageRating(), dto.reviewCount(),
                        dto.watcherCount()
                ));

        // when
        ContentDto result = contentService.create(request, file);

        // then
        assertThat(result.thumbnailUrl()).isEqualTo(presignedUrl);

        verify(writer).create(request, file);
        verify(fileService).getFileUrlByKey(key);
        verify(contentMapper).withThumbnailUrl(dto, presignedUrl);
    }

    @Test
    @DisplayName("외부 썸네일 URL이 있는 콘텐츠는 기존 URL을 반환한다.")
    void getContent_externalThumbnail() {
        // given
        UUID contentId = UUID.randomUUID();
        String thumbnailUrl = "https://example.com/thumbnail.jpg";

        ContentDto dto = mock(ContentDto.class);
        ContentDto expected = mock(ContentDto.class);

        given(dto.thumbnailUrl()).willReturn(thumbnailUrl);
        given(reader.getById(contentId))
                .willReturn(new ContentResult(dto, null));
        given(contentMapper.withThumbnailUrl(dto, thumbnailUrl))
                .willReturn(expected);

        // when
        ContentDto result = contentService.getById(contentId);

        // then
        assertThat(result).isSameAs(expected);

        verify(reader).getById(contentId);
        verify(contentMapper).withThumbnailUrl(dto, thumbnailUrl);
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("S3 썸네일이 있는 콘텐츠는 Presigned URL을 반환한다.")
    void getContent_s3Thumbnail() {
        // given
        UUID contentId = UUID.randomUUID();
        String fileKey = "contents/test-thumbnail.jpg";
        String presignedUrl = "https://s3.example.com/test-thumbnail.jpg";

        ContentDto dto = mock(ContentDto.class);
        ContentDto expected = mock(ContentDto.class);

        given(reader.getById(contentId))
                .willReturn(new ContentResult(dto, fileKey));

        given(fileService.getFileUrlByKey(fileKey))
                .willReturn(presignedUrl);

        given(contentMapper.withThumbnailUrl(dto, presignedUrl))
                .willReturn(expected);

        // when
        ContentDto result = contentService.getById(contentId);

        // then
        assertThat(result).isSameAs(expected);

        verify(reader).getById(contentId);
        verify(fileService).getFileUrlByKey(fileKey);
        verify(contentMapper).withThumbnailUrl(dto, presignedUrl);
    }

    @Test
    @DisplayName("콘텐츠 수정 후 Presigned URL을 포함한 DTO를 반환한다.")
    void update_success() {
        // given
        UUID contentId = UUID.randomUUID();

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

        given(contentMapper.withThumbnailUrl(dto, presignedUrl))
                .willReturn(expected);

        // when
        ContentDto result = contentService.update(contentId, request, null);

        // then
        assertThat(result).isSameAs(expected);

        verify(writer).update(contentId, request, null);
        verify(fileService).getFileUrlByKey(key);
        verify(contentMapper).withThumbnailUrl(dto, presignedUrl);
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
