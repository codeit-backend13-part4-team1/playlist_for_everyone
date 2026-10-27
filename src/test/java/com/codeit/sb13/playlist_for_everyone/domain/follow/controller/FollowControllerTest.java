package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowDto;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;
import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.WebMvcConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.SelfFollowNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test
    @DisplayName("팔로우 대상 ID가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void follow_returnsBadRequestWhenFolloweeIdMissing() throws Exception {
        mockMvc.perform(post("/api/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.details.followeeId").isArray());

        verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("팔로우 대상 ID의 UUID 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void follow_returnsBadRequestWhenFolloweeIdInvalid() throws Exception {
        mockMvc.perform(post("/api/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                       {
                         "followeeId": "not-a-uuid"
                       }
                       """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value(HttpMessageNotReadableException.class.getSimpleName()));

        verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("자기 팔로우가 거부되면 400과 공통 오류 응답을 반환한다.")
    void follow_returnsBadRequestWhenSelfFollowRejected() throws Exception {
        UUID followeeId = UUID.randomUUID();
        FollowCommand command = new FollowCommand(followeeId, null);

        given(followService.follow(command)).willThrow(new SelfFollowNotAllowedException());

        mockMvc.perform(post("/api/follows").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "followeeId":"%s"
                        }
                        """.formatted(followeeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value(SelfFollowNotAllowedException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.SELF_FOLLOW_NOT_ALLOWED.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verify(followService).follow(command);
    }

    @Test
    @DisplayName("팔로우 대상 사용자가 없으면 400과 대상 ID를 포함한 공통 오류 응답을 반환한다.")
    void follow_returnsBadRequestWhenFolloweeNotFound() throws Exception {
        UUID followeeId = UUID.randomUUID();
        FollowCommand command = new FollowCommand(followeeId, null);

        given(followService.follow(command))
                .willThrow(new  FollowTargetNotFoundException(Map.of("followeeId", followeeId)));

        mockMvc.perform(post("/api/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "followeeId": "%s"
                    }
                    """.formatted(followeeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value(FollowTargetNotFoundException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.FOLLOW_TARGET_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.details.followeeId").value(followeeId.toString()));

        verify(followService).follow(command);
    }

    @Test
    @DisplayName("팔로우 취소 요청을 서비스에 전달하고 본문 없이 204를 반환한다.")
    void unfollow_returnsNoContent() throws Exception {
        UUID followId = UUID.randomUUID();

        // 인증 연동 전에는 서비스에 전달할 followerId를 null로 둔다.
        UnFollowCommand command = new UnFollowCommand(followId, null);

        mockMvc.perform(delete("/api/follows/{followId}", followId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(followService).unfollow(command);

    }

    @Test
    @DisplayName("팔로우 ID의 UUID 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void unfollow_returnsBadRequestWhenFollowIdInvalid() throws Exception {
        mockMvc.perform(delete("/api/follows/{followId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value(MethodArgumentTypeMismatchException.class.getSimpleName()));

        verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("팔로워 수를 조회하면 200과 숫자 응답을 반환한다.")
    void getFollowCount_returnsFollowerCount() throws Exception {
        UUID followeeId = UUID.randomUUID();
        given(followService.countFollowers(followeeId)).willReturn(3L);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("3"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(followService).countFollowers(followeeId);
    }

    @Test
    @DisplayName("팔로워 수가 0이면 200과 숫자 0을 반환한다.")
    void getFollowCount_returnsZeroWhenNoFollowers() throws Exception {
        UUID followeeId = UUID.randomUUID();

        given(followService.countFollowers(followeeId)).willReturn(0L);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("0"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(followService).countFollowers(followeeId);
    }

    @Test
    @DisplayName("팔로워 수 조회 시 대상 ID가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void getFollowCount_returnsBadRequestWhenFolloweeIdMissing() throws Exception {
        mockMvc.perform(get("/api/follows/count"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details.followeeId").isArray());

        verifyNoInteractions(followService);
    }

    @Test
    @DisplayName("팔로워 수 조회 시 대상 ID의 UUID 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void getFollowCount_returnsBadRequestWhenFolloweeIdInvalid() throws Exception {
        mockMvc.perform(get("/api/follows/count")
                        .param("followeeId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details.followeeId").isArray());

        verifyNoInteractions(followService);
    }


}