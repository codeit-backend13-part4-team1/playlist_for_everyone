package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller;

import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.shaded.com.google.common.base.Verify;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;

@WebMvcTest(PlaylistController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaylistControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @MockitoBean
  private PlaylistService playlistService;

  @Test
  @DisplayName("올바른 요청에는 201응답과 생성된 플레이리스트를 반환한다")
  void 플레이리스트_생성_요청이_올바르면_201을_반환한다() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");

    UserSummary summary = new UserSummary(ownerId, "tester", null);

    PlaylistResponse response = new PlaylistResponse(
        playlistId, summary, "title", "description",
        LocalDateTime.of(2026, 10, 26,
            12, 0), 0L, false);

    given(playlistService.create(eq(ownerId), any(PlaylistCreateRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc.perform(post("/api/playlists")
            .header("X-User-Id", ownerId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(playlistId.toString()))
        .andExpect(jsonPath("$.title").value("title"))
        .andExpect(jsonPath("$.description").value("description"))
        .andExpect(jsonPath("$.owner.userId").value(ownerId.toString()))
        .andExpect(jsonPath("$.owner.name").value("tester"))
        .andExpect(jsonPath("$.subscriberCount").value(0))
        .andExpect(jsonPath("$.subscribedByMe").value(false))
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());
  }

  @Test
  @DisplayName("제목이 비어 있으면 400으로 응답한다.")
  void 제목이_비어있으면_400응답() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("", "description");

    // when & then
    mockMvc.perform(post("/api/playlists")
            .header("X-User-Id", ownerId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details.title").exists());
  }

  @Test
  @DisplayName("서비스에서 UserNotFoundException을 던지면 404로 응답한다.")
  void UserNotFoundException은_404로_응답한다() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");
    given(playlistService.create(eq(ownerId), any(PlaylistCreateRequest.class)))
        .willThrow(new UserNotFoundException(Map.of("userId", ownerId)));

    // when & then
    mockMvc.perform(post("/api/playlists")
            .header("X-User-Id", ownerId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("UserNotFoundException"))
        .andExpect(jsonPath("$.details.userId").value(ownerId.toString()));

  }

  @Test
  @DisplayName("요청한 값과 헤더의 사용자 ID를 서비스에 전달한다.")
  void 요청값과_사용자ID를_서비스에_전달한다() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");
    UserSummary summary = new UserSummary(ownerId, "tester", null);

    PlaylistResponse response = createResponse(playlistId, summary, false);

    given(playlistService.create(eq(ownerId), any(PlaylistCreateRequest.class)))
        .willReturn(response);

    // when
    mockMvc.perform(post("/api/playlists")
        .header("X-User-Id", ownerId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)));

    // then
    verify(playlistService).create(ownerId, request);
  }

  @Test
  @DisplayName("정상적인 조회 요청시 플레이 리스트 객체를 반환한다.")
  void 정상적인_조회_청시_200으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UserSummary summary = new UserSummary(ownerId, "tester", null);

    PlaylistResponse response = createResponse(playlistId, summary, true);

    given(playlistService.getPlaylist(playlistId, requesterId))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
            .header("X-User-Id", requesterId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(playlistId.toString()))
        .andExpect(jsonPath("$.title").value("title"))
        .andExpect(jsonPath("$.owner.name").value("tester"))
        .andExpect(jsonPath("$.description").value("description"))
        .andExpect(jsonPath("$.owner.userId").value(ownerId.toString()))
        .andExpect(jsonPath("$.subscriberCount").value(3))
        .andExpect(jsonPath("$.subscribedByMe").value(true));
  }

  @Test
  @DisplayName("서비스에 올바른 인자로 위임한다.")
  void 서비스에_경로변수와_헤더를_올바른_인자로_위임한다() throws Exception {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UserSummary summary = new UserSummary(ownerId, "tester", null);

    PlaylistResponse response = createResponse(playlistId, summary, true);

    given(playlistService.getPlaylist(playlistId, requesterId))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
        .header("X-User-Id", requesterId));
    verify(playlistService).getPlaylist(playlistId, requesterId);
  }

  @Test
  @DisplayName("없는 플레이 리스트 조회")
  void 없는_플레이리스트_조회시_404로_응답한다() throws Exception {
    // given
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    given(playlistService.getPlaylist(playlistId, requesterId))
        .willThrow(new PlayListNotFoundException(Map.of("playlistId", playlistId)));

    // when & then
    mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
        .header("X-User-Id", requesterId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("PlayListNotFoundException"))
        .andExpect(jsonPath("$.details.playlistId").value(playlistId.toString()));
  }

  @Test
  @DisplayName("올바르지 않은 id형식으로 조회")
  void id형식이_잘못된_형식이면_400으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();

    // when & then
    mockMvc.perform(get("/api/playlists/{playlistId}", "not-a-uuid")
        .header("X-User-Id", requesterId))
        .andExpect(status().isBadRequest());
    verify(playlistService, never()).getPlaylist(any(), any());
  }

  @Test
  @DisplayName("요청 헤더에 요청한 User Id 누락")
  void 헤더에_UserId_누락시_400으로_응답한다() throws Exception {
    // given
    UUID playlistId = UUID.randomUUID();

    // when & then
    mockMvc.perform(get("/api/playlists/{playlistId}", playlistId))
        .andExpect(status().isBadRequest());
    verify(playlistService, never()).getPlaylist(any(), any());

  }

  @Test
  @DisplayName("올바른 요청에는 200과 커서 페이지 응답을 반환한다")
  void 목록_조회_요청이_올바르면_200을_반환한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistResponse item = new PlaylistResponse(
        playlistId, new UserSummary(ownerId, "tester", null),
        "title", "description",
        LocalDateTime.of(2026, 10, 26, 12, 0), 3L, true);
    CursorResponse<PlaylistResponse> response = new CursorResponse<>(
        List.of(item), "2026-10-26T12:00:00", playlistId, true, 25L,
        "updatedAt", SortDirection.DESCENDING);

    given(playlistService.getPlaylists(any(PlaylistSearchRequest.class), eq(requesterId)))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/playlists")
            .header("X-User-Id", requesterId.toString())
            .param("limit", "10")
            .param("sortBy", "updatedAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(playlistId.toString()))
        .andExpect(jsonPath("$.data[0].owner.userId").value(ownerId.toString()))
        .andExpect(jsonPath("$.data[0].subscribedByMe").value(true))
        .andExpect(jsonPath("$.nextCursor").value("2026-10-26T12:00:00"))
        .andExpect(jsonPath("$.nextIdAfter").value(playlistId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(25))
        .andExpect(jsonPath("$.sortBy").value("updatedAt"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
  }

  @Test
  @DisplayName("쿼리 파라미터와 헤더를 요청 객체와 요청자 ID로 변환해 서비스에 전달한다")
  void 쿼리_파라미터와_헤더를_변환해_서비스에_전달한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    PlaylistSearchRequest expected = new PlaylistSearchRequest(
        "rock", ownerId, subscriberId,
        "5", idAfter,
        5, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING);
    CursorResponse<PlaylistResponse> empty = new CursorResponse<>(
        List.of(), null, null, false, 0L, "subscribeCount", SortDirection.DESCENDING);

    given(playlistService.getPlaylists(eq(expected), eq(requesterId))).willReturn(empty);

    // when
    mockMvc.perform(get("/api/playlists")
            .header("X-User-Id", requesterId.toString())
            .param("keywordLike", "rock")
            .param("ownerIdEqual", ownerId.toString())
            .param("subscriberIdEqual", subscriberId.toString())
            .param("cursor", "5")
            .param("idAfter", idAfter.toString())
            .param("limit", "5")
            .param("sortBy", "subscribeCount")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk());

    // then
    verify(playlistService).getPlaylists(eq(expected), eq(requesterId));
  }

  @Test
  @DisplayName("X-User-Id 헤더가 없으면 400으로 응답한다")
  void 목록_조회시_헤더가_없으면_400을_반환한다() throws Exception {
    // when & then
    mockMvc.perform(get("/api/playlists")
            .param("limit", "10")
            .param("sortBy", "updatedAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isBadRequest());

    verify(playlistService, never()).getPlaylists(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"limit", "sortBy", "sortDirection"})
  @DisplayName("필수 쿼리 파라미터가 없으면 400으로 응답한다")
  void 필수_쿼리_파라미터가_없으면_400을_반환한다(String missing) throws Exception {
    // given
    Map<String, String> params = validParams();
    params.remove(missing);

    // when & then
    mockMvc.perform(requestWith(params))
        .andExpect(status().isBadRequest());

    verify(playlistService, never()).getPlaylists(any(), any());
  }

  @ParameterizedTest
  @CsvSource({
      "limit, 0",
      "limit, abc",
      "sortBy, createdAt",
      "sortDirection, UP",
      "ownerIdEqual, abc",
      "subscriberIdEqual, abc",
      "idAfter, abc",
      "cursor, abc"
  })
  @DisplayName("쿼리 파라미터 값이 올바르지 않으면 400으로 응답한다")
  void 쿼리_파라미터_값이_올바르지_않으면_400을_반환한다(String name, String value) throws Exception {
    // given
    Map<String, String> params = validParams();
    params.put(name, value);

    // when & then
    mockMvc.perform(requestWith(params))
        .andExpect(status().isBadRequest());

    verify(playlistService, never()).getPlaylists(any(), any());
  }



  //-------[헬퍼 메서드]-----
  //PlaylistResponse 조립 헬퍼
  private PlaylistResponse createResponse(UUID playlistId, UserSummary summary, boolean subscribedByMe) {
    return new PlaylistResponse(
        playlistId, summary, "title", "description",
        LocalDateTime.of(2026, 10, 26,
            12, 0), 3L, subscribedByMe);
  }

  private Map<String, String> validParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("limit", "10");
    params.put("sortBy", "updatedAt");
    params.put("sortDirection", "DESCENDING");
    return params;
  }

  private MockHttpServletRequestBuilder requestWith(Map<String, String> params) {
    MockHttpServletRequestBuilder builder = get("/api/playlists")
        .header("X-User-Id", UUID.randomUUID().toString());
    params.forEach(builder::param);
    return builder;
  }

}