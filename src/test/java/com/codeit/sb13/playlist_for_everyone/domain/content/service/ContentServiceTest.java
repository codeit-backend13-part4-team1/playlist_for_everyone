package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Content 서비스 테스트")
public class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ContentMapper contentMapper;

    @InjectMocks
    private ContentService contentService;

    @Test
    @DisplayName("관리자 콘텐츠를 정상적으로 생성한다.")
    void create_success() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        MultipartFile file = mock(MultipartFile.class);

        when(contentRepository.save(any(Content.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        contentService.create(request, file);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);

        verify(contentRepository).save(captor.capture());

        Content savedContent = captor.getValue();

        assertThat(savedContent.getType()).isEqualTo(ContentType.tvSeries);
        assertThat(savedContent.getTitle()).isEqualTo("너말고다른연애");
        assertThat(savedContent.getDescription()).isEqualTo("KBS 드라마 입니다.");
        assertThat(savedContent.getThumbnailUrl())
                .isEqualTo("temporary-thumbnail-url");
        assertThat(savedContent.getProvider()).isEqualTo(ContentSource.ADMIN);

        assertThat(savedContent.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("드라마", "로맨스");

        verify(contentMapper).toDto(savedContent, 0.0, 0, 0);
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

        when(contentRepository.save(any(Content.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        contentService.create(request, file);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);

        verify(contentRepository).save(captor.capture());

        Content savedContent = captor.getValue();

        assertThat(savedContent.getTags())
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

        when(contentRepository.save(any(Content.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        contentService.create(request, file);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);

        verify(contentRepository).save(captor.capture());

        Content savedContent = captor.getValue();

        assertThat(savedContent.getTags()).isEmpty();
    }

    @Test
    @DisplayName("태그가 null이어도 콘텐츠가 정상적으로 생성된다.")
    void create_nullTags() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null
        );

        MultipartFile file = mock(MultipartFile.class);

        when(contentRepository.save(any(Content.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        contentService.create(request, file);

        // then
        ArgumentCaptor<Content> captor =
                ArgumentCaptor.forClass(Content.class);

        verify(contentRepository).save(captor.capture());

        assertThat(captor.getValue().getTags()).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 수정 시 제목, 설명, 태그가 변경되고 리뷰 집계가 전달된다.")
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

        MultipartFile file = mock(MultipartFile.class);

        when(contentRepository.findById(contentId))
                .thenReturn(Optional.of(content));

        // 리뷰 평균 평점과 리뷰 개수 설정
        when(reviewRepository.findAverageRatingByContentId(contentId))
                .thenReturn(4.5);

        when(reviewRepository.countByContentId(contentId))
                .thenReturn(3L);

        // 수정 전 상태 확인
        assertThat(content.getTitle()).isEqualTo("너말고다른연애");
        assertThat(content.getDescription()).isEqualTo("KBS 드라마 입니다.");
        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("드라마");

        // when
        contentService.update(contentId, request, file);

        // then
        assertThat(content.getTitle()).isEqualTo("너말고 다른 연애");
        assertThat(content.getDescription()).isEqualTo("서강준, 안은진 주연");
        assertThat(content.getThumbnailUrl()).isEqualTo("thumbnail-url");

        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("로맨스");

        verify(contentRepository).findById(contentId);

        verify(contentMapper).toDto(content, 4.5, 3, 0L);
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

        when(contentRepository.findById(contentId))
                .thenReturn(Optional.empty());

        MultipartFile file = mock(MultipartFile.class);

        // when & then
        assertThatThrownBy(() -> contentService.update(contentId, request, file))
                .isInstanceOf(ContentNotFoundException.class);

        verify(contentRepository).findById(contentId);
        verifyNoInteractions(reviewRepository, contentMapper);
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

        when(contentRepository.findById(contentId))
                .thenReturn(Optional.of(content));

        MultipartFile file = mock(MultipartFile.class);

        // when
        contentService.update(contentId, request, file);

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

        when(contentRepository.findById(contentId))
                .thenReturn(Optional.of(content));

        // when
        contentService.update(contentId, request, null);

        // then
        assertThat(content.getTitle()).isEqualTo("너말고다른연애");
        assertThat(content.getDescription()).isEqualTo("KBS 드라마 입니다.");

        assertThat(content.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("로맨스");
    }

}
