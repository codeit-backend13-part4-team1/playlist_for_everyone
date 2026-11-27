package com.codeit.sb13.playlist_for_everyone.domain.content.controller;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentSearchService;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.watching.service.WatchingSessionHttpService;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc
@DisplayName("ContentController 테스트")
public class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private ContentMapper contentMapper;

    @MockitoBean
    private ContentService contentService;

    @MockitoBean
    private ContentSearchService contentSearchService;

    @MockitoBean
    private WatchingSessionHttpService watchingSessionService;

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
        String presignedUrl = "https://example.com/thumbnail.jpg";

        ContentDto dto = new ContentDto(
                contentId,
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                presignedUrl,
                List.of("드라마", "로맨스"),
                0.0, 0, 0L
        );

        given(contentService.create(
                any(ContentCreateRequest.class),
                any(MultipartFile.class)
        )).willReturn(dto);

        // when & then
        mockMvc.perform(multipart("/api/contents")
                        .file(createJsonRequestPart(request))
                        .file(createThumbnail())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("너말고다른연애"))
                .andExpect(jsonPath("$.thumbnailUrl").value(presignedUrl))
                .andExpect(jsonPath("$.tags[0]").value("드라마"));

        verify(contentService).create(
                any(ContentCreateRequest.class),
                any(MultipartFile.class)
        );
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
    @WithMockUser
    @DisplayName("콘텐츠 검색 요청 시 검색 결과를 반환한다.")
    void searchContent_success() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();
        UUID contentId2 = UUID.randomUUID();

        ContentSearchRequest request = new ContentSearchRequest(
                null,
                null,
                null,
                null,
                null,
                2,
                SortDirection.DESCENDING,
                "createdAt"
        );

        ContentDto dto1 = new ContentDto(
                contentId,
                ContentType.movie,
                "테스트 영화",
                "테스트 설명",
                "https://example.com/thumbnail.jpg",
                List.of("액션"),
                4.5,
                2,
                10L
        );

        ContentDto dto2 = new ContentDto(
                contentId2, ContentType.movie,
                "영화2", "설명2", "thumbnail2",
                List.of("드라마"), 4.5, 3, 5L
        );

        given(contentSearchService.search(request.toCondition()))
                .willReturn(new CursorResponse<>(
                        List.of(dto1, dto2),
                        "2026-11-10T09:00:00",
                        contentId2,
                        true,
                        3L,
                        request.toCondition().sortBy(),
                        request.toCondition().sortDirection()
                ));

        // when & then
        mockMvc.perform(get("/api/contents")
                        .param("limit", "2")
                        .param("sortBy", "createdAt")
                        .param("sortDirection", "DESCENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor")
                        .value("2026-11-10T09:00:00"))
                .andExpect(jsonPath("$.nextIdAfter")
                        .value(contentId2.toString()))
                .andExpect(jsonPath("$.totalCount").value(3));

        verify(contentSearchService).search(request.toCondition());
    }

    @Test
    @WithMockUser
    @DisplayName("콘텐츠 단건 조회 요청 시 200 OK와 ContentDto를 반환한다.")
    void getContent_success() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();
        String thumbnailUrl = "https://example.com/thumbnail.jpg";

        ContentDto contentDto = new ContentDto(
                contentId,
                ContentType.tvSeries,
                "너 말고 다른 연애",
                "서강준, 안은진 주연",
                thumbnailUrl,
                List.of("드라마", "로맨스"),
                4.5,
                10,
                0L
        );

        given(contentService.getById(contentId))
                .willReturn(contentDto);

        // when & then
        mockMvc.perform(get("/api/contents/{contentId}", contentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("너 말고 다른 연애"))
                .andExpect(jsonPath("$.thumbnailUrl").value(thumbnailUrl))
                .andExpect(jsonPath("$.tags[0]").value("드라마"))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(10))
                .andExpect(jsonPath("$.watcherCount").value(0));

        verify(contentService).getById(contentId);
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 콘텐츠 조회 요청 시 404 Not Found를 반환한다.")
    void getContent_notFound() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        when(contentService.getById(contentId))
                .thenThrow(new ContentNotFoundException(
                        Map.of("contentId", contentId)
                ));

        // when & then
        mockMvc.perform(get("/api/contents/{contentId}", contentId))
                .andExpect(status().isNotFound());

        verify(contentService).getById(contentId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("콘텐츠 수정 요청 시 200 OK와 ContentDto를 반환한다.")
    void update_success() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();
        String presignedUrl = "https://example.com/thumbnail.jpg";

        ContentUpdateRequest request = new ContentUpdateRequest(
                "너 말고 다른 연애",
                "서강준, 안은진 주연",
                List.of("드라마", "로맨스")
        );

        ContentDto dto = new ContentDto(
                contentId, ContentType.tvSeries,
                "너 말고 다른 연애", "서강준, 안은진 주연",
                presignedUrl, List.of("드라마", "로맨스"),
                4.5, 10, 0L
        );

        when(contentService.update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                isNull()
        )).thenReturn(dto);

// when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(createJsonRequestPart(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("너 말고 다른 연애"))
                .andExpect(jsonPath("$.thumbnailUrl").value(presignedUrl))
                .andExpect(jsonPath("$.tags[0]").value("드라마"))
                .andExpect(jsonPath("$.tags[1]").value("로맨스"));

        verify(contentService).update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                isNull()
        );
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

        ContentDto dto = new ContentDto(
                contentId, ContentType.tvSeries,
                "기존 제목", "기존 설명",
                null, List.of("로맨스"),
                4.5, 10, 0L
        );

        when(contentService.update(
                eq(contentId),
                any(ContentUpdateRequest.class),
                isNull()
        )).thenReturn(dto);

        // when & then
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/contents/{contentId}", contentId)
                        .file(createJsonRequestPart(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contentId.toString()))
                .andExpect(jsonPath("$.title").value("기존 제목"))
                .andExpect(jsonPath("$.description").value("기존 설명"))
                .andExpect(jsonPath("$.tags[0]").value("로맨스"));

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

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 콘텐츠 삭제를 성공하면 204를 반환한다.")
    void deleteContent_success() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/contents/{contentId}", contentId)
                        .with(csrf()))
                        .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(contentService).deleteById(contentId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 콘텐츠 삭제 요청 시 404 Not Found를 반환한다.")
    void deleteContent_notFound() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        doThrow(new ContentNotFoundException(Map.of("contentId", contentId)))
                .when(contentService).deleteById(contentId);

        // when & then
        mockMvc.perform(delete("/api/contents/{contentId}", contentId)
                        .with(csrf()))
                        .andExpect(status().isNotFound());

        verify(contentService).deleteById(contentId);
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
