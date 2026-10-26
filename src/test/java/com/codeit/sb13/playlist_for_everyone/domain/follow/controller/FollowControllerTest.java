package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.WebMvcConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FollowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, MDCLoggingInterceptor.class})
@DisplayName("팔로우 컨트롤러")
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @Test
    @DisplayName("신규 팔로우를 등록하면 201과 생성된 팔로우 정보를 반환한다.")
    void follow_returnsCreatedWhenNewFollow() throws Exception {
        UUID followId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        // 인증 연동 전에는 서비스에 전달할 followerId를 null로 둔다.
        FollowCommand command = new FollowCommand(followeeId, null);
        FollowDto response = new FollowDto(followId, followeeId, followerId);
        given(followService.follow(command)).willReturn(FollowResult.followCreated(response));

        mockMvc.perform(post("/api/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "followeeId": "%s"
                        }
                        """.formatted(followeeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(followId.toString()))
                .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
                .andExpect(jsonPath("$.followerId").value(followerId.toString()));

        verify(followService).follow(command);
    }

    @Test
    @DisplayName("이미 팔로우 중이면 200과 기존 팔로우 정보를 반환한다.")
    void follow_returnsOkWhenAlreadyFollowed() throws Exception {
        UUID existingFollowId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        FollowCommand command = new FollowCommand(followeeId, null);
        FollowDto response = new FollowDto(existingFollowId, followeeId, followerId);

        given(followService.follow(command)).willReturn(FollowResult.alreadyFollowed(response));
        mockMvc.perform(post("/api/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {
                                "followeeId": "%s"
                            }
                        """.formatted(followeeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingFollowId.toString()))
                .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
                .andExpect(jsonPath("$.followerId").value(followerId.toString()));

        verify(followService).follow(command);
    }


}