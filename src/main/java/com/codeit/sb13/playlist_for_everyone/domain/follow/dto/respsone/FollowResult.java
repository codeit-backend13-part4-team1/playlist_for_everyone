package com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone;

/**
 * 등록 서비스가 컨트롤러에 전달하는 결과.
 * created는 HTTP 상태(신규 201, 기존 200)를 선택하는 데 사용하며 응답 본문은 followDto만 전달한다.
 */
public record FollowResult(FollowDto followDto, boolean created) {

    public static FollowResult alreadyFollowed(FollowDto followDto){
        return new FollowResult(followDto, false);
    }

    public static FollowResult followCreated(FollowDto followDto){
        return new FollowResult(followDto, true);
    }
}
