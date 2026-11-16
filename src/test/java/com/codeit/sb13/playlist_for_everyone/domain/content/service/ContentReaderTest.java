package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.ContentRatingProjection;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 조회 서비스 테스트")
public class ContentReaderTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ContentMapper contentMapper;

    @InjectMocks
    private ContentReader reader;

    @Test
    @DisplayName("존재하는 콘텐츠를 단건 조회한다.")
    void getContent_success() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "thumbnail-url",
                null
        );

        ContentDetailProjection detail = mock(ContentDetailProjection.class);
        ContentDto expected = mock(ContentDto.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));

        given(detail.getContent()).willReturn(content);
        given(detail.getAverageRating()).willReturn(4.5);
        given(detail.getReviewCount()).willReturn(3L);

        given(contentMapper.toDto(
                content, "thumbnail-url", 4.5, 3, 0L
        )).willReturn(expected);

        // when
        ContentResult<ContentDto> result = reader.getById(contentId);

        // then
        assertThat(result.dto()).isSameAs(expected);
        assertThat(result.fileKey()).isNull();

        verify(contentRepository).findDetailById(contentId);
        verify(contentMapper).toDto(
                content, "thumbnail-url", 4.5, 3, 0L
        );
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠를 조회하면 예외가 발생한다.")
    void getContent_unexists() {
        // given
        UUID contentId = UUID.randomUUID();

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reader.getById(contentId))
                .isInstanceOf(ContentNotFoundException.class)
                .satisfies(exception -> {
                    ContentNotFoundException e =
                            (ContentNotFoundException) exception;

                    assertThat(e.getDetails())
                            .containsEntry("contentId", contentId);
                });
    }

    @Test
    @DisplayName("콘텐츠 ID 목록으로 콘텐츠와 리뷰 집계를 일괄 조회한다")
    void getSummariesByIds_success() {
        // given
        UUID contentId1 = UUID.randomUUID();
        UUID contentId2 = UUID.randomUUID();

        Content content1 = mock(Content.class);
        Content content2 = mock(Content.class);

        when(content1.getId()).thenReturn(contentId1);
        when(content1.getType()).thenReturn(ContentType.movie);
        when(content1.getTitle()).thenReturn("영화1");
        when(content1.getDescription()).thenReturn("설명1");
        when(content1.getThumbnailUrl()).thenReturn("https://example.com/1.jpg");
        when(content1.getTags()).thenReturn(List.of());

        when(content2.getId()).thenReturn(contentId2);
        when(content2.getType()).thenReturn(ContentType.movie);
        when(content2.getTitle()).thenReturn("영화2");
        when(content2.getDescription()).thenReturn("설명2");
        when(content2.getThumbnailUrl()).thenReturn("https://example.com/2.jpg");
        when(content2.getTags()).thenReturn(List.of());

        ContentRatingProjection rating1 = mock(ContentRatingProjection.class);
        when(rating1.getContentId()).thenReturn(contentId1);
        when(rating1.getAverageRating()).thenReturn(4.5);
        when(rating1.getReviewCount()).thenReturn(3L);

        List<UUID> contentIds = List.of(contentId1, contentId2);

        when(contentRepository.findAllByIds(contentIds))
                .thenReturn(List.of(content2, content1));

        when(reviewRepository.findRatingsByContentIds(contentIds))
                .thenReturn(List.of(rating1));

        // when
        List<ContentResult<ContentSummary>> results =
                reader.getSummariesByIds(contentIds);

        // then
        assertThat(results).hasSize(2);

        assertThat(results.get(0).dto().contentId()).isEqualTo(contentId1);
        assertThat(results.get(0).dto().title()).isEqualTo("영화1");
        assertThat(results.get(0).dto().averageRating()).isEqualTo(4.5);
        assertThat(results.get(0).dto().reviewCount()).isEqualTo(3);

        assertThat(results.get(1).dto().contentId()).isEqualTo(contentId2);
        assertThat(results.get(1).dto().averageRating()).isZero();
        assertThat(results.get(1).dto().reviewCount()).isZero();

        verify(contentRepository, times(1)).findAllByIds(contentIds);
        verify(reviewRepository, times(1)).findRatingsByContentIds(contentIds);
    }

    @Test
    @DisplayName("콘텐츠 ID 목록이 비어 있으면 조회하지 않는다")
    void getSummariesByIds_empty() {
        // when
        List<ContentResult<ContentSummary>> results =
                reader.getSummariesByIds(List.of());

        // then
        assertThat(results).isEmpty();

        verifyNoInteractions(contentRepository, reviewRepository);
    }

}
