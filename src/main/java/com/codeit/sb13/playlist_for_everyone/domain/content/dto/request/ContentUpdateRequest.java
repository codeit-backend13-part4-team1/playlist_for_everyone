package com.codeit.sb13.playlist_for_everyone.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContentUpdateRequest(
        @Pattern(
                regexp = "(?U).*\\S.*",
                message = "제목은 공백만 입력할 수 없습니다."
        )
        @Size(max = 100)
        String title,

        @Pattern(
                regexp = "(?sU).*\\S.*",
                message = "설명은 공백만 입력할 수 없습니다."
        )
        String description,

        List<@Size(max = 50) String> tags
) {
}
