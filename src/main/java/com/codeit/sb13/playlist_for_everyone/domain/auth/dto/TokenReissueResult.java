package com.codeit.sb13.playlist_for_everyone.domain.auth.dto;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;

/**
 * 토큰 재발급 서비스의 내부 반환값
 *
 * jwtDto는 HTTP 응답 본문으로 사용하고,
 * refreshToken은 쿠키를 만드는데 사용한다.
 *
 * 해당 객체 전체를 Controller 응답 본문으로 반환하면 안 된다.
 */

public record TokenReissueResult(
        JwtDto jwtDto,
        String refreshToken
) {
}
