package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistCreateRequest(

    @NotBlank
    @Size(max = 100)
    String title,
    @NotBlank
    @Size(max = 255)
    String description

) {

}
