package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.cache.ContentSearchCacheEvictEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexUpsertEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.event.WebSocketEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 생성/수정/삭제 서비스 테스트")
public class ContentWriterTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private PlaylistContentRepository playlistContentRepository;

    @Mock
    private FileService fileService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WebSocketEventFactory webSocketEventFactory;

    @Mock
    private ContentMapper contentMapper;

    @InjectMocks
    private ContentWriter writer;

    private FileInfo mockFileUpload(MultipartFile file) {
        FileInfo fileInfo = mock(FileInfo.class);
        given(fileService.upload(file)).willReturn(fileInfo);
        return fileInfo;
    }

    @Test
    @DisplayName("관리자 콘텐츠를 정상적으로 생성하고 캐시 무효화 이벤트를 발행한다.")
    void create_success() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        MultipartFile file = mock(MultipartFile.class);
        FileInfo fileInfo = mockFileUpload(file);

        String key = "contents/test-thumbnail.jpg";
        given(fileInfo.getKey()).willReturn(key);

        given(contentRepository.save(any(Content.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ContentResult result = writer.create(request, file);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());

        Content savedContent = captor.getValue();

        assertThat(savedContent)
                .extracting(
                        Content::getType,
                        Content::getTitle,
                        Content::getDescription,
                        Content::getThumbnailUrl,
                        Content::getProvider
                )
                .containsExactly(
                        ContentType.tvSeries,
                        "너말고다른연애",
                        "KBS 드라마 입니다.",
                        null,
                        ContentSource.ADMIN
                );

        assertThat(savedContent.getFileInfo()).isSameAs(fileInfo);
        assertThat(savedContent.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("드라마", "로맨스");

        verify(fileService).upload(file);
        verify(contentMapper).toDto(
                savedContent,
                null,
                0.0, 0, 0L
        );

        assertThat(result.fileKey()).isEqualTo(fileInfo.getKey());
        verify(eventPublisher).publishEvent(any(ContentIndexUpsertEvent.class));
    }

    @Test
    @DisplayName("태그의 불필요한 공백과 중복을 제거한다.")
    void create_normalizeTags() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마  ", "드 라 마", "로맨스", "  ", "KBS")
        );

        MultipartFile file = mock(MultipartFile.class);
        mockFileUpload(file);

        when(contentRepository.save(any(Content.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        writer.create(request, file);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());

        assertThat(captor.getValue().getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("드라마", "로맨스", "KBS");
    }

    @Test
    @DisplayName("태그가 없어도 콘텐츠가 정상적으로 생성된다.")
    void create_withoutTags() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of()
        );

        MultipartFile file = mock(MultipartFile.class);
        mockFileUpload(file);

        when(contentRepository.save(any(Content.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        writer.create(request, file);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);

        verify(contentRepository).save(captor.capture());

        Content savedContent = captor.getValue();

        assertThat(savedContent.getTags()).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 수정 시 변경 사항과 집계 정보를 반영하고 캐시 무효화 이벤트를 발행한다.")
    void update_success() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        content.addTag("드라마");

        ContentUpdateRequest request = new ContentUpdateRequest(
                "너말고 다른 연애",
                "서강준, 안은진 주연",
                List.of("로맨스")
        );

        ContentDetailProjection detail = mock(ContentDetailProjection.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));

        given(detail.getContent()).willReturn(content);
        given(detail.getAverageRating()).willReturn(4.5);
        given(detail.getReviewCount()).willReturn(3L);

        // when
        writer.update(contentId, request, null);

        // then
        assertThat(content.getTitle()).isEqualTo("너말고 다른 연애");
        assertThat(content.getDescription()).isEqualTo("서강준, 안은진 주연");
        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("로맨스");

        verify(contentRepository).findDetailById(contentId);
        verify(contentMapper).toDto(
                content,
                "thumbnail-url",
                4.5, 3, 0L);

        verify(eventPublisher).publishEvent(any(ContentIndexUpsertEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠 수정 시 예외가 발생한다.")
    void update_notFound() {
        // given
        UUID contentId = UUID.randomUUID();

        ContentUpdateRequest request = new ContentUpdateRequest(
                "너말고 다른연애",
                "서강준 주연",
                List.of("드라마")
        );

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.empty());

        MultipartFile file = mock(MultipartFile.class);

        // when & then
        assertThatThrownBy(() -> writer.update(contentId, request, file))
                .isInstanceOf(ContentNotFoundException.class);

        verify(contentRepository).findDetailById(contentId);
        verifyNoInteractions(contentMapper);
    }

    @Test
    @DisplayName("태그가 null이면 기존 태그를 유지한 채 콘텐츠를 수정한다.")
    void update_nullTags() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        content.addTag("드라마");

        ContentUpdateRequest request = new ContentUpdateRequest(
                "너 말고 다른 연애",
                "수정된 설명",
                null
        );

        ContentDetailProjection detail = mock(ContentDetailProjection.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));
        given(detail.getContent()).willReturn(content);

        // when
        writer.update(contentId, request, null);

        // then
        assertThat(content.getTitle()).isEqualTo("너 말고 다른 연애");
        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("드라마");
    }

    @Test
    @DisplayName("제목과 설명이 null이면 기존 값을 유지하고 태그만 수정한다.")
    void update_nullTitleAndDescription() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );
        content.addTag("드라마");

        ContentUpdateRequest request = new ContentUpdateRequest(
                null,
                null,
                List.of("로맨스")
        );

        ContentDetailProjection detail = mock(ContentDetailProjection.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));
        given(detail.getContent()).willReturn(content);

        // when
        writer.update(contentId, request, null);

        // then
        assertThat(content.getTitle()).isEqualTo("너말고다른연애");
        assertThat(content.getDescription()).isEqualTo("KBS 드라마 입니다.");

        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("로맨스");
    }

    @Test
    @DisplayName("썸네일 수정 시 정상적으로 수정된다.")
    void update_thumbnail_success() {
        // given
        UUID contentId = UUID.randomUUID();

        // 기존 파일 정보
        UUID oldFileId = UUID.randomUUID();

        FileInfo oldFileInfo = mock(FileInfo.class);
        when(oldFileInfo.getId()).thenReturn(oldFileId);

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null,
                oldFileInfo
        );

        // 새 파일 정보
        MultipartFile file = mock(MultipartFile.class);
        FileInfo newFileInfo = mockFileUpload(file);

        ContentDetailProjection detail = mock(ContentDetailProjection.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));

        given(detail.getContent()).willReturn(content);

        // when
        writer.update(contentId, null, file);

        // then
        assertThat(content.getThumbnailUrl()).isNull();
        assertThat(content.getFileInfo()).isSameAs(newFileInfo);

        InOrder inOrder = inOrder(fileService, contentRepository);

        inOrder.verify(fileService).upload(file);
        inOrder.verify(contentRepository).flush();
        inOrder.verify(fileService).delete(oldFileId);
    }

    @Test
    @DisplayName("기존 파일 정보가 없으면 새 썸네일만 등록한다.")
    void update_thumbnail_withoutOldFile() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null,
                null
        );

        // 새 파일 정보
        MultipartFile file = mock(MultipartFile.class);
        FileInfo newFileInfo = mockFileUpload(file);

        String key = "contents/new-thumbnail.jpg";
        given(newFileInfo.getKey()).willReturn(key);

        ContentDetailProjection detail = mock(ContentDetailProjection.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));

        given(detail.getContent()).willReturn(content);

        // when
        writer.update(contentId, null, file);

        // then
        assertThat(content.getThumbnailUrl()).isNull();
        assertThat(content.getFileInfo()).isSameAs(newFileInfo);

        verify(fileService).upload(file);
        verify(contentRepository).flush();
        verify(fileService, never()).delete(any());
    }

    @Test
    @DisplayName("빈 파일을 전달하면 기존 썸네일을 유지한다.")
    void update_thumbnail_emptyFile() {
        // given
        UUID contentId = UUID.randomUUID();
        String oldThumbnailUrl = "https://example.com/old.jpg";

        MultipartFile file = mock(MultipartFile.class);
        FileInfo oldFileInfo = mock(FileInfo.class);

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                oldThumbnailUrl,
                oldFileInfo
        );

        when(file.isEmpty()).thenReturn(true);

        ContentDetailProjection detail = mock(ContentDetailProjection.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));

        given(detail.getContent()).willReturn(content);

        // when
        writer.update(contentId, null, file);

        // then
        assertThat(content.getThumbnailUrl()).isEqualTo(oldThumbnailUrl);
        assertThat(content.getFileInfo()).isSameAs(oldFileInfo);

        verify(fileService, never()).upload(any());
        verify(fileService, never()).delete(any());
    }

    @Test
    @DisplayName("콘텐츠 삭제 시 연결된 리뷰, 파일, 플레이리스트 콘텐츠를 삭제하고 캐시 무효화 이벤트를 발행한다.")
    void deleteById_success() {
        // given
        UUID contentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        FileInfo fileInfo = mock(FileInfo.class);
        given(fileInfo.getId()).willReturn(fileId);

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url",
                fileInfo
        );

        given(contentRepository.findById(contentId))
                .willReturn(Optional.of(content));

        // when
        writer.deleteById(contentId);

        // then
        InOrder inOrder = inOrder(
                reviewRepository,
                playlistContentRepository,
                contentRepository,
                fileService
        );

        inOrder.verify(reviewRepository).deleteAllByContentId(contentId);
        inOrder.verify(playlistContentRepository).deleteAllByContentId(contentId);
        inOrder.verify(contentRepository).delete(content);
        inOrder.verify(contentRepository).flush();
        inOrder.verify(fileService).delete(fileId);

        verify(eventPublisher).publishEvent(any(ContentIndexDeleteEvent.class));
        verify(webSocketEventFactory).contentDeleteEvent(contentId);
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠를 삭제하면 예외가 발생한다.")
    void deleteById_notFound() {
        // given
        UUID contentId = UUID.randomUUID();

        given(contentRepository.findById(contentId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> writer.deleteById(contentId))
                .isInstanceOf(ContentNotFoundException.class)
                .satisfies(exception -> {
                    ContentNotFoundException e =
                            (ContentNotFoundException) exception;

                    assertThat(e.getDetails())
                            .containsEntry("contentId", contentId);
                });

        then(contentRepository).should().findById(contentId);
        then(reviewRepository).shouldHaveNoInteractions();
        then(playlistContentRepository).shouldHaveNoInteractions();
        then(contentRepository).should(never()).delete(any(Content.class));
    }

    @Test
    @DisplayName("파일 정보가 없는 콘텐츠도 정상적으로 삭제한다.")
    void deleteById_withoutFile() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url"
        );

        given(contentRepository.findById(contentId))
                .willReturn(Optional.of(content));

        // when
        writer.deleteById(contentId);

        // then
        verify(contentRepository).delete(content);
        verify(fileService, never()).delete(any());

        verify(eventPublisher).publishEvent(any(ContentIndexDeleteEvent.class));
        verify(webSocketEventFactory).contentDeleteEvent(contentId);
    }

}
