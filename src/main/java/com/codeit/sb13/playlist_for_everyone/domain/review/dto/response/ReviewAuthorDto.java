package com.codeit.sb13.playlist_for_everyone.domain.review.dto.response;

import javax.swing.*;
import java.util.UUID;

public record ReviewAuthorDto (
        UUID authorId,
        String nickname,
        String profileImageUrl
) {
}
