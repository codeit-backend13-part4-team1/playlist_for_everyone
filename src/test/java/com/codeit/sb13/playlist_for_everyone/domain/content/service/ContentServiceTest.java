package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Content 서비스 테스트")
public class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;

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

}
