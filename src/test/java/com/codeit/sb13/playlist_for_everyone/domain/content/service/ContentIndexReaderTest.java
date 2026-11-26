package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentDocumentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentIndexReader 테스트")
public class ContentIndexReaderTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentDocumentMapper contentDocumentMapper;

    @InjectMocks
    private ContentIndexReader contentIndexReader;

    @Test
    @DisplayName("콘텐츠를 배치 단위로 조회하여 Document로 변환한다.")
    void readDocuments_success() {
        // given
        UUID contentId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 500);

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null
        );

        ContentDocument document = ContentDocument.builder()
                .id(contentId.toString())
                .type(ContentType.tvSeries)
                .title("너말고다른연애")
                .description("KBS 드라마 입니다.")
                .build();

        Slice<UUID> ids = new SliceImpl<>(List.of(contentId), pageable, false);

        given(contentRepository.findAllIds(pageable)).willReturn(ids);
        given(contentRepository.findAllByIds(List.of(contentId))).willReturn(List.of(content));
        given(contentDocumentMapper.toContentDocument(content)).willReturn(document);

        // when
        Slice<ContentDocument> result = contentIndexReader.readDocuments(pageable);

        // then
        assertThat(result.getContent()).containsExactly(document);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.getPageable()).isEqualTo(pageable);

        verify(contentRepository).findAllIds(pageable);
        verify(contentRepository).findAllByIds(List.of(contentId));
        verify(contentDocumentMapper).toContentDocument(content);
    }

    @Test
    @DisplayName("조회된 콘텐츠 ID가 없으면 빈 Slice를 반환한다.")
    void readDocuments_empty() {
        // given
        Pageable pageable = PageRequest.of(0, 500);

        Slice<UUID> ids = new SliceImpl<>(List.of(), pageable, false);

        given(contentRepository.findAllIds(pageable)).willReturn(ids);

        // when
        Slice<ContentDocument> result = contentIndexReader.readDocuments(pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.getPageable()).isEqualTo(pageable);

        verify(contentRepository).findAllIds(pageable);
        verify(contentRepository, never()).findAllByIds(anyCollection());
        verifyNoInteractions(contentDocumentMapper);
    }

    @Test
    @DisplayName("다음 배치가 존재하면 hasNext가 true를 반환한다.")
    void readDocuments_hasNext() {
        // given
        UUID contentId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 1);

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                null
        );

        ContentDocument document = ContentDocument.builder()
                .id(contentId.toString())
                .type(ContentType.tvSeries)
                .title("너말고다른연애")
                .description("KBS 드라마 입니다.")
                .build();

        Slice<UUID> ids = new SliceImpl<>(List.of(contentId), pageable, true);

        given(contentRepository.findAllIds(pageable)).willReturn(ids);
        given(contentRepository.findAllByIds(List.of(contentId))).willReturn(List.of(content));
        given(contentDocumentMapper.toContentDocument(content)).willReturn(document);

        // when
        Slice<ContentDocument> result = contentIndexReader.readDocuments(pageable);

        // then
        assertThat(result.getContent()).containsExactly(document);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.getPageable()).isEqualTo(pageable);
        assertThat(result.nextPageable())
                .isEqualTo(PageRequest.of(1, 1));
    }

    @Test
    @DisplayName("콘텐츠 ID로 조회하면 ContentDocument를 반환한다.")
    void readDocument_findById() {
        // given
        UUID contentId = UUID.randomUUID();

        Content content = Content.createAdminContent(
                ContentType.tvSeries,
                "굿파트너2",
                "장나라, 김혜윤 주연",
                null
        );

        ContentDocument document = ContentDocument.builder()
                .id(contentId.toString())
                .title("굿파트너2")
                .build();

        given(contentRepository.findAllByIds(List.of(contentId)))
                .willReturn(List.of(content));
        given(contentDocumentMapper.toContentDocument(content))
                .willReturn(document);

        // when
        var result = contentIndexReader.readDocument(contentId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(document);

        verify(contentRepository).findAllByIds(List.of(contentId));
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠 ID로 조회하면 빈 Optional을 반환한다.")
    void readDocument_notFound() {
        // given
        UUID contentId = UUID.randomUUID();

        given(contentRepository.findAllByIds(List.of(contentId))).willReturn(List.of());

        // when
        var result = contentIndexReader.readDocument(contentId);

        // then
        assertThat(result).isEmpty();

        verify(contentRepository).findAllByIds(List.of(contentId));
    }

}
