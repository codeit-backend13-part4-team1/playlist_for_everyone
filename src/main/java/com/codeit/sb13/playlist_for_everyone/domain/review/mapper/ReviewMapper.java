package com.codeit.sb13.playlist_for_everyone.domain.review.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewAuthorDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewMapper {

    public ReviewDto toDto(Review review) {
        User author = review.getAuthor();

        ReviewAuthorDto authorDto = new ReviewAuthorDto(
                author.getId(),
                author.getName(),
                null
        );

        return new ReviewDto(
                review.getId(),
                review.getContent().getId(),
                authorDto,
                review.getText(),
                review.getRating()
        );
    }

}
