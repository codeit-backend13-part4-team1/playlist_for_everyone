package com.codeit.sb13.playlist_for_everyone.domain.playlist.controller;

import static org.mockito.BDDMockito.given;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request.PlaylistCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistResponse;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");
    UserSummary owner = new UserSummary(ownerId, "tester", null);
    PlaylistResponse response = new PlaylistResponse(
        UUID.randomUUID(), owner, "title", "description",
        LocalDateTime.now(), 0, false);
    given(playlistService.create(eq(ownerId), any(PlaylistCreateRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc.perform(post("/api/playlists")
            .header("X-User-Id", ownerId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("title"));
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
        .andDo(print())
        .andExpect(status().isBadRequest());
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
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", "description");
    UserSummary owner = new UserSummary(ownerId, "tester", null);
    PlaylistResponse response = new PlaylistResponse(
        UUID.randomUUID(), owner, "title", "description",
        LocalDateTime.now(), 0, false);
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

}