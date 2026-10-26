package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller;

import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
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
  @DisplayName("정상적인 조회 요총시 플레이 리스트 객체를 반환한다.")
  void 정상적인_조회_청시_200으로_응답한다() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UserSummary summary = new UserSummary(ownerId, "tester", null);

    PlaylistResponse response = createResponse(playlistId, summary, true);

    given(playlistService.getPlaylist(ownerId, playlistId))
        .willReturn(response);

    // when & then
    mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
            .header("X-User-Id", requesterId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(playlistId.toString()))
        .andExpect(jsonPath("$.title").value("title"))
        .andExpect(jsonPath("$.description").value("description"))
        .andExpect(jsonPath("$.owner.userId").value(ownerId.toString()))
        .andExpect(jsonPath("$.description").value("description"))
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



  //-------[헬퍼 메서드]-----
  //PlaylistResponse 조립 헬퍼
  private PlaylistResponse createResponse(UUID playlistId, UserSummary summary, boolean subscribedByMe) {
    return new PlaylistResponse(
        playlistId, summary, "title", "description",
        LocalDateTime.of(2026, 10, 26,
            12, 0), 3L, subscribedByMe);
  }

}