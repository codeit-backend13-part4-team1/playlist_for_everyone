package com.codeit.sb13.playlist_for_everyone.domain.review.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.service.ReviewService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        ReviewDto response = new ReviewDto(
                reviewId,
                contentId,
                null,
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
    @DisplayName("리뷰 내용이 비어 있으면 400 Bad Request를 반환한다.")
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
    @DisplayName("평점이 0.0이면 400 Bad Request를 반환한다.")
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

}
