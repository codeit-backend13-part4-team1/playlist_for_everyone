package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record PlaylistUpdateRequest(
    @Size(max = 100)
    String title,
    @Size(max = 255)
    String description
) {

  @AssertTrue(message = "title 또는 description 중 하나는 입력해야 합니다")
  public boolean isUpdatable() {
    return title != null || description != null;
  }

  @AssertTrue(message = "title은 공백일 수 없습니다")
  public boolean isTitleNotBlank() {
    return title == null || !title.isBlank();
  }

  @AssertTrue(message = "description은 공백일 수 없습니다")
  public boolean isDescriptionNotBlank() {
    return description == null || !description.isBlank();
  }

}
