package com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;

/**
 *  로그인 성공 응답
 *
 *  사용자 정보와 이후 API 요청에 사용할 Access Token을 반환한다.
 *  비밀번호와 Refresh Token은 이 응답 DTO에 포함하지 않는다.
 */
public record JwtDto(
        UserDto userDto,
        String accessToken
) {
}
