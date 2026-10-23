package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response;

import java.util.UUID;

public record UserSummary(
    //컴파일을 위한 임시 클래스
    UUID userID,
    String username,
    String profileImageUrl
) {

}
