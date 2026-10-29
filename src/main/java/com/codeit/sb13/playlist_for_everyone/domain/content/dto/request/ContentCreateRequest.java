package com.codeit.sb13.playlist_for_everyone.domain.content.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContentCreateRequest(
        @NotNull
        ContentType type,

        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        String description,

        List<@Size(max  =50) String> tags
) {
}
