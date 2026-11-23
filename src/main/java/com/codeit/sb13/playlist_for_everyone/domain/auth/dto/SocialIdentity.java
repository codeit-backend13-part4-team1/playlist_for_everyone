package com.codeit.sb13.playlist_for_everyone.domain.auth.dto;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;

/**
 * 제공자별 사용자 저보를 공통 형태로 바꾼 값
 *
 * @param providerUserId Google sub, Kakao id
 * @param email Google 이메일. Kakao는 제공하지 않아 null
 * @param name Google 이름, Kakao 닉네임. 없으면 null
 * @param emailVerified Google이 이메일 소유를 확인했는지 여부. Kakao는 false
 */
public record SocialIdentity(
        SocialProvider provider,
        String providerUserId,
        String email,
        String name,
        boolean emailVerified
) {
}
