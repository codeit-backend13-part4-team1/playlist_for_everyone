package com.codeit.sb13.playlist_for_everyone.domain.content.controller;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ContentController 테스트")
public class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContentService contentService;

    @Test
    @DisplayName("정상 요청 시 콘텐츠가 생성된다.")
    void create_success() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        // when & then
        // 201 응답 확인
        mockMvc.perform(multipart("/api/contents")
                        .file(createRequestPart(request))
                        .file(createThumbnail()))
                .andExpect(status().isCreated());

        // 콘텐츠 생성 서비스 호출 확인
        // 서비스에 전달된 요청 데이터 확인
        ArgumentCaptor<ContentCreateRequest> captor =
                ArgumentCaptor.forClass(ContentCreateRequest.class);

        verify(contentService).create(
                captor.capture(),
                any(MultipartFile.class)
        );

        ContentCreateRequest capturedRequest = captor.getValue();

        assertThat(capturedRequest.type()).isEqualTo(ContentType.tvSeries);
        assertThat(capturedRequest.title()).isEqualTo("너말고다른연애");
        assertThat(capturedRequest.description()).isEqualTo("KBS 드라마 입니다.");
        assertThat(capturedRequest.tags()).containsExactly("드라마", "로맨스");
    }

    @Test
    @DisplayName("콘텐츠 타입이 없을 때 400을 응답한다.")
    void create_invalidType() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                null,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(createRequestPart(request))
                        .file(createThumbnail()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @DisplayName("제목이 비어 있을 때 400을 응답한다.")
    void create_invalidTitle() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(createRequestPart(request))
                        .file(createThumbnail()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @DisplayName("설명이 비어 있을 때 400을 응답한다.")
    void create_invalidDescription() throws Exception {
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "    ",
                List.of("드라마", "로맨스")
        );

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(createRequestPart(request))
                        .file(createThumbnail()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @DisplayName("썸네일이 없을 때 400을 응답한다.")
    void create_withoutThumbnail() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(createRequestPart(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    private MockMultipartFile createRequestPart(ContentCreateRequest request) {
        return new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );
    }

    private MockMultipartFile createThumbnail() {
        return new MockMultipartFile(
                "thumbnail",
                "thumbnail.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test-image".getBytes()
        );
    }

}
