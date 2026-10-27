package com.codeit.sb13.playlist_for_everyone.domain.content.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ContentCreateRequest(
        @NotNull
        ContentType type,

        @NotBlank
        String title,

        @NotBlank
        String description,

        List<String> tags
) {
}
