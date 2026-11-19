package com.codeit.sb13.playlist_for_everyone.domain.review.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewAuthorDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewResult;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
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

    public ReviewResult toResult(Review review) {
        ReviewDto dto = toDto(review);

        User author = review.getAuthor();
        FileInfo profileImage = author.getProfileImage();

        String profileImageKey = profileImage == null
                ? null
                : profileImage.getKey();

        return new ReviewResult(
                dto,
                profileImageKey
        );
    }

    public ReviewDto withProfileImageUrl(
            ReviewDto dto,
            String profileImageUrl
    ) {
        ReviewAuthorDto author = dto.authorDto();

        ReviewAuthorDto updatedAuthor = new ReviewAuthorDto(
                author.authorId(),
                author.nickname(),
                profileImageUrl
        );

        return new ReviewDto(
                dto.id(),
                dto.contentId(),
                updatedAuthor,
                dto.text(),
                dto.rating()
        );
    }

}
