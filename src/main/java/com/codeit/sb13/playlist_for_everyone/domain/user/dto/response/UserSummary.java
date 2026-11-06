package com.codeit.sb13.playlist_for_everyone.domain.user.dto.response;

import java.util.UUID;

/**
 * 여러 도메인에서 공통으로 사용하는 사용자 요약 정보.
 * 프로필 이미지가 없으면 profileImageUrl은 null이다.
 */

public record UserSummary(
        UUID userId,
        String name,
        String profileImageUrl
) {
}
