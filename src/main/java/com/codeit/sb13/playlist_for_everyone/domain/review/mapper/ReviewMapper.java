package com.codeit.sb13.playlist_for_everyone.domain.review.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewMapper {

    private final UserDtoMapper userMapper;

    public ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getContent().getId(),
                userMapper.toDto(review.getAuthor()),
                review.getText(),
                review.getRating()
        );
    }

}
