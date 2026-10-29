package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller;

import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListForbiddenException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

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
            .with(loginAs(ownerId))
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
            .with(loginAs(ownerId))
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
            .with(loginAs(ownerId))
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
        .with(loginAs(ownerId))
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
        .with(loginAs(requesterId)))
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
        .with(loginAs(requesterId)));
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
            .with(loginAs(requesterId)))
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
            .with(loginAs(requesterId)))
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
            .with(loginAs(requesterId))
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
            .with(loginAs(requesterId))
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

  @Test
  @DisplayName("올바른 수정 요청에는 200과 수정된 플레이리스트를 반환한다")
  void 플레이리스트_수정_요청이_올바르면_200을_반환한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UserSummary summary = new UserSummary(requesterId, "tester", null);
    PlaylistResponse response = new PlaylistResponse(
        playlistId, summary, "새 제목", "새 설명",
        LocalDateTime.of(2026, 10, 28, 12, 0), 3L, false);

    // when
    Map<String, String> body = Map.of("title", "새 제목", "description", "새 설명");

    given(playlistService.updatePlaylist(
        eq(playlistId), any(PlaylistUpdateRequest.class), eq(requesterId)))
        .willReturn(response);

    // when & then
    mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
            .with(loginAs(requesterId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(playlistId.toString()))
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.description").value("새 설명"))
        .andExpect(jsonPath("$.owner.userId").value(requesterId.toString()))
        .andExpect(jsonPath("$.owner.name").value("tester"));
  }

  @Test
  @DisplayName("경로변수, 요청 본문, 헤더의 사용자 ID를 서비스에 전달한다")
  void 수정_요청값을_서비스에_올바르게_전달한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UserSummary summary = new UserSummary(requesterId, "tester", null);
    given(playlistService.updatePlaylist(
        eq(playlistId), any(PlaylistUpdateRequest.class), eq(requesterId)))
        .willReturn(createResponse(playlistId, summary, false));

    // when
    mockMvc.perform(patchRequest(playlistId, requesterId,
        Map.of("title", "새 제목", "description", "새 설명")));

    // then
    verify(playlistService).updatePlaylist(
        playlistId, new PlaylistUpdateRequest("새 제목", "새 설명"), requesterId);
  }

  @Test
  @DisplayName("title과 description이 모두 없으면 400으로 응답한다")
  void 수정_본문이_비어_있으면_400으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patchRequest(playlistId, requesterId, Map.of()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details.updatable").exists());

    verify(playlistService, never()).updatePlaylist(any(), any(), any());
  }

  @Test
  @DisplayName("title이 공백이면 400으로 응답한다")
  void 수정_title이_공백이면_400으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patchRequest(playlistId, requesterId, Map.of("title", " ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.titleNotBlank").exists());

    verify(playlistService, never()).updatePlaylist(any(), any(), any());
  }

  @Test
  @DisplayName("title이 100자를 넘으면 400으로 응답한다")
  void 수정_title이_너무_길면_400으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patchRequest(playlistId, requesterId, Map.of("title", "a".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.title").exists());

    verify(playlistService, never()).updatePlaylist(any(), any(), any());
  }

  @Test
  @DisplayName("playlistId가 UUID 형식이 아니면 400으로 응답한다")
  void 수정_id형식이_잘못되면_400으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();

    // when & then
    mockMvc.perform(patch("/api/playlists/{playlistId}", "not-a-uuid")
            .with(loginAs(requesterId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("title", "새 제목"))))
        .andExpect(status().isBadRequest());

    verify(playlistService, never()).updatePlaylist(any(), any(), any());
  }

  @Test
  @DisplayName("서비스에서 PlayListNotFoundException을 던지면 404로 응답한다")
  void 없는_플레이리스트_수정시_404로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    given(playlistService.updatePlaylist(
        eq(playlistId), any(PlaylistUpdateRequest.class), eq(requesterId)))
        .willThrow(new PlayListNotFoundException(Map.of("playlistId", playlistId)));

    // when & then
    mockMvc.perform(patchRequest(playlistId, requesterId, Map.of("title", "새 제목")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("PlayListNotFoundException"))
        .andExpect(jsonPath("$.details.playlistId").value(playlistId.toString()));
  }

  @Test
  @DisplayName("서비스에서 PlayListForbiddenException을 던지면 403으로 응답한다")
  void 소유자가_아닌_수정_요청은_403으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    given(playlistService.updatePlaylist(
        eq(playlistId), any(PlaylistUpdateRequest.class), eq(requesterId)))
        .willThrow(new PlayListForbiddenException(
            Map.of("playlistId", playlistId, "requesterId", requesterId)));

    // when & then
    mockMvc.perform(patchRequest(playlistId, requesterId, Map.of("title", "새 제목")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("PlayListForbiddenException"))
        .andExpect(jsonPath("$.details.playlistId").value(playlistId.toString()))
        .andExpect(jsonPath("$.details.requesterId").value(requesterId.toString()));
  }


  @Test
  @DisplayName("올바른 삭제 요청에는 200으로 응답하고 서비스에 경로변수와 헤더를 전달한다")
  void 플레이리스트_삭제_요청이_올바르면_200을_반환한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    // when & then
    mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
            .with(loginAs(requesterId)))
        .andExpect(status().isOk());

    verify(playlistService).deletePlaylist(playlistId, requesterId);
  }

  @Test
  @DisplayName("playlistId가 UUID 형식이 아니면 400으로 응답한다")
  void 삭제_id형식이_잘못되면_400으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();

    // when & then
    mockMvc.perform(delete("/api/playlists/{playlistId}", "not-a-uuid")
            .with(loginAs(requesterId)))
        .andExpect(status().isBadRequest());

    verify(playlistService, never()).deletePlaylist(any(), any());
  }

  @Test
  @DisplayName("서비스에서 PlayListNotFoundException을 던지면 404로 응답한다")
  void 없는_플레이리스트_삭제시_404로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    willThrow(new PlayListNotFoundException(Map.of("playlistId", playlistId)))
        .given(playlistService).deletePlaylist(playlistId, requesterId);

    // when & then
    mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
            .with(loginAs(requesterId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("PlayListNotFoundException"))
        .andExpect(jsonPath("$.details.playlistId").value(playlistId.toString()));
  }

  @Test
  @DisplayName("서비스에서 PlayListForbiddenException을 던지면 403으로 응답한다")
  void 소유자가_아닌_삭제_요청은_403으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    willThrow(new PlayListForbiddenException(
        Map.of("playlistId", playlistId, "requesterId", requesterId)))
        .given(playlistService).deletePlaylist(playlistId, requesterId);

    // when & then
    mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
            .with(loginAs(requesterId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("PlayListForbiddenException"))
        .andExpect(jsonPath("$.details.playlistId").value(playlistId.toString()))
        .andExpect(jsonPath("$.details.requesterId").value(requesterId.toString()));
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
        .with(loginAs(UUID.randomUUID()));
    params.forEach(builder::param);
    return builder;
  }

  private MockHttpServletRequestBuilder patchRequest(
      UUID playlistId, UUID requesterId, Object body) throws Exception {
    return patch("/api/playlists/{playlistId}", playlistId)
        .with(loginAs(requesterId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private RequestPostProcessor loginAs(UUID userId) {
    SecurityUser principal = new SecurityUser(userId, "tester@example.com", "", Role.USER, false);
    return SecurityMockMvcRequestPostProcessors.authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
  }

}