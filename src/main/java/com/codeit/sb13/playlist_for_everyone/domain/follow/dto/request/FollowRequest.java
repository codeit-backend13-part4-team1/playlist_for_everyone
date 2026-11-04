package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 등록 본문과 조회 쿼리에서 공통으로 사용하는 대상 사용자 입력.
 * 요청자 ID는 입력 필드로 받지 않고 컨트롤러가 Principal에서 추출한다.
 * 필수 값·UUID 형식은 MVC에서 검증하고, 자기 팔로우 여부는 서비스에서 판단한다.
 */
public record FollowRequest(@NotNull UUID followeeId) {

    public FollowCommand toFollowCommand(UUID followerId) {
        return new FollowCommand(followeeId, followerId);
    }
}
