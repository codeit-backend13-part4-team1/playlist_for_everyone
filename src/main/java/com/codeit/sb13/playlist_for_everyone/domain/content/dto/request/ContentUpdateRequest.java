package com.codeit.sb13.playlist_for_everyone.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ContentUpdateRequest(
        String title,
        String description,
        List<String> tags
) {
}
