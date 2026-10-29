package com.codeit.sb13.playlist_for_everyone.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContentUpdateRequest(
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 100)
        String title,

        @Pattern(regexp = ".*\\S.*")
        String description,

        List<@Size(max = 50) String> tags
) {
}
