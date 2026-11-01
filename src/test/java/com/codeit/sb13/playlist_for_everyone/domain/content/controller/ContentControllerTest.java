package com.codeit.sb13.playlist_for_everyone.domain.content.controller;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc
@Import(ContentControllerTest.MethodSecurityTestConfig.class)
@DisplayName("ContentController 테스트")
public class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContentService contentService;

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityTestConfig {
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("정상 요청 시 201 Created와 ContentDto를 반환한다.")
    void create_success() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        UUID contentId = UUID.randomUUID();

        ContentDto response = new ContentDto(
                contentId,
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                "https://example.com/thumbnail.jpg",
                List.of("드라마", "로맨스"),
                0.0,
                0,
                0L
        );

        when(contentService.create(
                any(ContentCreateRequest.class),
                any(MultipartFile.class)
        )).thenReturn(response);

        // when & then
        // 201 응답 확인
        mockMvc.perform(multipart("/api/contents")
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("너말고다른연애"))
                .andExpect(jsonPath("$.description").value("KBS 드라마 입니다."))
                .andExpect(jsonPath("$.tags[0]").value("드라마"))
                .andExpect(jsonPath("$.tags[1]").value("로맨스"));

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
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자는 콘텐츠를 생성할 수 없다.")
    void create_forbidden() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                List.of("액션")
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnail",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image".getBytes()
        );

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(requestPart)
                        .file(thumbnail)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("콘텐츠 생성 시 제목이 100자를 초과하면 400을 응답한다.")
    void create_titleTooLong() throws Exception {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "가".repeat(101),
                "서강준, 안은진 주연",
                List.of("드라마", "로맨스")
        );

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
                        .file(createJsonRequestPart(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("콘텐츠 수정 요청 시 200 OK와 ContentDto를 반환한다.")
    void update_success() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ContentUpdateRequest request = new ContentUpdateRequest(
                "너 말고 다른 연애",
                "서강준, 안은진 주연",
                List.of("드라마", "로맨스")
        );

        ContentDto response = new ContentDto(
                contentId,
                ContentType.tvSeries,
                "너 말고 다른 연애",
                "서강준, 안은진 주연",
                "https://example.com/thumbnail.jpg",
                List.of("드라마", "로맨스"),
                4.5,
                10,
                0L
        );

        when(contentService.update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                isNull()
        )).thenReturn(response);

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(createJsonRequestPart(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("너 말고 다른 연애"))
                .andExpect(jsonPath("$.description").value("서강준, 안은진 주연"))
                .andExpect(jsonPath("$.tags[0]").value("드라마"))
                .andExpect(jsonPath("$.tags[1]").value("로맨스"));

        // 서비스에 전달된 요청 데이터 확인
        ArgumentCaptor<ContentUpdateRequest> captor =
                ArgumentCaptor.forClass(ContentUpdateRequest.class);

        verify(contentService).update(
                eq(contentId),
                captor.capture(),
                isNull()
        );

        ContentUpdateRequest capturedRequest = captor.getValue();

        assertThat(capturedRequest.title()).isEqualTo("너 말고 다른 연애");
        assertThat(capturedRequest.description()).isEqualTo("서강준, 안은진 주연");
        assertThat(capturedRequest.tags()).containsExactly("드라마", "로맨스");
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자는 콘텐츠를 수정할 수 없다.")
    void update_forbidden() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ContentUpdateRequest request = new ContentUpdateRequest(
                "수정된 제목",
                "수정된 설명",
                List.of("드라마")
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(requestPart)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("수정 시 제목과 설명이 없어도 정상적으로 수정된다.")
    void update_withoutTitleAndDescription() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ContentUpdateRequest request = new ContentUpdateRequest(
                null,
                null,
                List.of("로맨스")
        );

        ContentDto response = new ContentDto(
                contentId,
                ContentType.tvSeries,
                "기존 제목",
                "기존 설명",
                "https://example.com/thumbnail.jpg",
                List.of("로맨스"),
                0.0,
                0,
                0L
        );

        when(contentService.update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                isNull()
        )).thenReturn(response);

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(createJsonRequestPart(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("기존 제목"))
                .andExpect(jsonPath("$.description").value("기존 설명"))
                .andExpect(jsonPath("$.tags[0]").value("로맨스"));;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("콘텐츠 수정 시 태그가 50자를 초과하면 400을 응답한다.")
    void update_tagTooLong() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ContentUpdateRequest request = new ContentUpdateRequest(
                null,
                null,
                List.of("가".repeat(51))
        );

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(createJsonRequestPart(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 콘텐츠 수정 시 404를 반환한다.")
    void update_notFound() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ContentUpdateRequest request = new ContentUpdateRequest(
                "너말고 다른연애",
                "서강준 주연",
                List.of("드라마")
        );

        when(contentService.update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                any(MultipartFile.class)
        )).thenThrow(new ContentNotFoundException(
                Map.of("contentId", contentId)
        ));

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(contentService).update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                any(MultipartFile.class)
        );
    }

    private MockMultipartFile createJsonRequestPart(Object request) {
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
