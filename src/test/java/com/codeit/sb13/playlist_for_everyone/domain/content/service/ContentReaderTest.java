package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 조회 서비스 테스트")
public class ContentReaderTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private WatchingSessionService watchingSessionService;

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
                "thumbnail-url"
        );

        ContentDetailProjection detail = mock(ContentDetailProjection.class);
        ContentDto expected = mock(ContentDto.class);

        given(contentRepository.findDetailById(contentId))
                .willReturn(Optional.of(detail));

        given(detail.getContent()).willReturn(content);
        given(detail.getAverageRating()).willReturn(4.5);
        given(detail.getReviewCount()).willReturn(3L);
        given(watchingSessionService.getCurrentViewerCount(contentId))
                .willReturn(3L);

        given(contentMapper.toDto(
                content, "thumbnail-url", 4.5, 3, 3L
        )).willReturn(expected);

        // when
        ContentResult result = reader.getById(contentId);

        // then
        assertThat(result.dto()).isSameAs(expected);
        assertThat(result.fileKey()).isNull();

        verify(contentRepository).findDetailById(contentId);
        verify(contentMapper).toDto(
                content, "thumbnail-url", 4.5, 3, 3L
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

}
