package com.codeit.sb13.playlist_for_everyone.domain.review.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewListRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.service.ReviewService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.review.ReviewNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("리뷰 컨트롤러 테스트")
public class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReviewService reviewService;

    /** 리뷰 생성 테스트 */
    @Test
    @DisplayName("리뷰 생성 요청 시 201 Created와 리뷰 정보를 반환한다.")
    void createReview() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        ReviewCreateRequest request = new ReviewCreateRequest(
                contentId,
                "재미있는 드라마였어요.",
                4.5
        );

        SecurityUser securityUser = new SecurityUser(
                authorId,
                "test@example.com",
                "encodedPassword",
                Role.USER,
                false
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,
                        null,
                        securityUser.getAuthorities()
                );

        UserDto userDto = mock(UserDto.class);

        ReviewDto response = new ReviewDto(
                reviewId,
                contentId,
                userDto,
                "재미있는 드라마였어요.",
                4.5
        );

        given(reviewService.save(any(ReviewCreateRequest.class), eq(authorId)))
                .willReturn(response);

        // when & then
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            mockMvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(reviewId.toString()))
                    .andExpect(jsonPath("$.contentId").value(contentId.toString()))
                    .andExpect(jsonPath("$.text").value("재미있는 드라마였어요."))
                    .andExpect(jsonPath("$.rating").value(4.5));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("리뷰 내용이 비어 있으면 400을 반환한다.")
    void createReview_invalidText() throws Exception {
        // given
        ReviewCreateRequest request = new ReviewCreateRequest(
                UUID.randomUUID(),
                "",
                4.5
        );

        // when & then
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("평점이 0.0이면 400을 반환한다.")
    void createReview_invalidRating() throws Exception {
        // given
        ReviewCreateRequest request = new ReviewCreateRequest(
                UUID.randomUUID(),
                "재미있는 드라마였어요.",
                0.0
        );

        // when & then
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("리뷰 목록 조회 시 200 OK와 페이지네이션 응답을 반환한다.")
    void getReviewList() throws Exception {
        // given
        UUID contentId = UUID.randomUUID();

        ReviewListRequest request = new ReviewListRequest(
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        ReviewListCondition condition = request.toCondition();

        UserDto userDto = mock(UserDto.class);

        ReviewDto review1 = new ReviewDto(
                UUID.randomUUID(),
                contentId,
                userDto,
                "정말 재미있었어요.",
                5.0
        );

        ReviewDto review2 = new ReviewDto(
                UUID.randomUUID(),
                contentId,
                userDto,
                "볼만한 드라마였어요.",
                4.0
        );

        ReviewDto review3 = new ReviewDto(
                UUID.randomUUID(),
                contentId,
                userDto,
                "조금 아쉬웠어요.",
                3.0
        );

        CursorResponse<ReviewDto> response = new CursorResponse<>(
                List.of(review1, review2, review3),
                null,
                null,
                false,
                3L,
                null,
                null
        );

        given(reviewService.getReviews(eq(contentId), eq(condition)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/reviews")
                        .param("contentId", contentId.toString())
                        .param("limit", "10")
                        .param("sortDirection", "DESCENDING")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(review1.id().toString()))
                .andExpect(jsonPath("$.data[1].text").value("볼만한 드라마였어요."))
                .andExpect(jsonPath("$.data[2].rating").value(3.0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalCount").value(3));

        then(reviewService).should().getReviews(contentId, condition);
    }

    @Test
    @DisplayName("콘텐츠 ID가 없으면 400 Bad Request를 반환한다.")
    void getReviewList_notFoundContentId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/reviews")
                        .param("limit", "10")
                        .param("sortDirection", "DESCENDING")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isBadRequest());

        then(reviewService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("limit이 없으면 400을 반환한다.")
    void getReviewList_missingLimit() throws Exception {
        // when & then
        mockMvc.perform(get("/api/reviews")
                        .param("contentId", UUID.randomUUID().toString())
                        .param("sortDirection", "DESCENDING")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isBadRequest());

        then(reviewService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정렬 방향이 없으면 400을 반환한다.")
    void getReviewList_missingSortDirection() throws Exception {
        // when & then
        mockMvc.perform(get("/api/reviews")
                        .param("contentId", UUID.randomUUID().toString())
                        .param("limit", "10")
                        .param("sortBy", "createdAt"))
                .andExpect(status().isBadRequest());

        then(reviewService).shouldHaveNoInteractions();
    }

    /** 리뷰 수정 테스트 */
    @Test
    @DisplayName("리뷰 수정 시 변경된 리뷰 정보를 반환한다.")
    void updateReview() throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "수정된 리뷰입니다.",
                4.5
        );

        ReviewDto response = new ReviewDto(
                reviewId,
                contentId,
                null,
                "수정된 리뷰입니다.",
                4.5
        );

        SecurityUser securityUser = new SecurityUser(
                authorId,
                "test@example.com",
                "encodedPassword",
                Role.USER,
                false
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,
                        null,
                        securityUser.getAuthorities()
                );

        given(reviewService.update(any(ReviewUpdateRequest.class), eq(reviewId), eq(authorId)))
                .willReturn(response);

        // when & then
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reviewId.toString()))
                    .andExpect(jsonPath("$.contentId").value(contentId.toString()))
                    .andExpect(jsonPath("$.text").value("수정된 리뷰입니다."))
                    .andExpect(jsonPath("$.rating").value(4.5));

            then(reviewService).should()
                    .update(any(ReviewUpdateRequest.class), eq(reviewId), eq(authorId));

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("리뷰 수정 시 텍스트가 null이면 요청 검증을 통과한다.")
    void updateReview_nullText() throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                null,
                4.0
        );

        SecurityUser securityUser = new SecurityUser(
                authorId,
                "test@example.com",
                "encodedPassword",
                Role.USER,
                false
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,
                        null,
                        securityUser.getAuthorities()
                );

        ReviewDto response = mock(ReviewDto.class);

        given(reviewService.update(
                any(ReviewUpdateRequest.class),
                eq(reviewId),
                eq(authorId)
        )).willReturn(response);

        // when & then
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            then(reviewService).should()
                    .update(
                            argThat(req -> req.text() == null),
                            eq(reviewId),
                            eq(authorId)
                    );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("리뷰 수정 시 평점이 범위를 벗어나면 400을 반환한다.")
    void updateReview_invalidRating() throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "수정된 리뷰입니다.",
                0.0
        );

        // when & then
        mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        then(reviewService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "   ",
            "\t",
            "\n",
            "\u00A0",
            "\u3000"
    })
    @DisplayName("텍스트에 공백이 입력되면 400을 반환한다.")
    void updateReview_invalidText(String text) throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                text,
                4.0
        );

        // when & then
        mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        then(reviewService).shouldHaveNoInteractions();
    }

    /** 리뷰 삭제 테스트 */
    @Test
    @DisplayName("리뷰 작성자가 삭제 요청 시 204를 반환한다.")
    void deleteReview() throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        SecurityUser securityUser = new SecurityUser(
                authorId,
                "test@example.com",
                "encodedPassword",
                Role.USER,
                false
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,
                        null,
                        securityUser.getAuthorities()
                );

        // when & then
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
                    .andExpect(status().isNoContent());

            then(reviewService).should().delete(reviewId, authorId);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("리뷰 작성자가 아니면 삭제 시 403을 반환한다.")
    void deleteReview_withoutPermission() throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        SecurityUser securityUser = new SecurityUser(
                otherUserId,
                "test@example.com",
                "encodedPassword",
                Role.USER,
                false
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,
                        null,
                        securityUser.getAuthorities()
                );

        willThrow(new AccessDeniedException("리뷰 작성자만 삭제할 수 있습니다."))
                .given(reviewService).delete(reviewId, otherUserId);

        // when & then
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
                    .andExpect(status().isForbidden());

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 삭제 시 404를 반환한다.")
    void deleteReview_invalidReviewId() throws Exception {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        SecurityUser securityUser = new SecurityUser(
                authorId,
                "test@example.com",
                "encodedPassword",
                Role.USER,
                false
        );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        securityUser,
                        null,
                        securityUser.getAuthorities()
                );

        willThrow(new ReviewNotFoundException(Map.of("reviewId", reviewId)))
                .given(reviewService).delete(reviewId, authorId);

        // when & then
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
                    .andExpect(status().isNotFound());

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
